package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleCounts;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.convert.TagConverter;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import com.echocyan.codenest.article.service.*;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.common.util.Texts;
import com.echocyan.codenest.counter.api.*;
import com.echocyan.codenest.framework.cache.BloomFilter;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService,
        CounterSource {

    /**
     * 自动摘要截取的正文字符数。
     */
    private static final int AUTO_SUMMARY_LENGTH = 100;

    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final ArticleConverter articleConverter;
    private final CategoryConverter categoryConverter;
    private final TagConverter tagConverter;
    private final UserApi userApi;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;
    private final TwoLevelCache<CachedArticleDetail> detailCache;
    private final TwoLevelCache<ArticleBrief> briefCache;
    private final BloomFilter articleBloomFilter;

    private static ArticleCounts countsOf(Counts counts) {
        return new ArticleCounts(
                counts.get(CounterMetric.ARTICLE_LIKE),
                counts.get(CounterMetric.ARTICLE_FAVORITE),
                counts.get(CounterMetric.ARTICLE_COMMENT),
                counts.get(CounterMetric.ARTICLE_VIEW));
    }

    /**
     * 请求中可由作者修改的字段。
     */
    private static Article articleOf(ArticleRequest request) {
        Article article = new Article();
        article.setTitle(request.title());
        article.setSummary(summaryOf(request));
        article.setCoverUrl(request.coverUrl());
        article.setCategoryId(request.categoryId());
        return article;
    }

    private static ArticleContent contentOf(long articleId, ArticleRequest request) {
        ArticleContent content = new ArticleContent();
        content.setArticleId(articleId);
        content.setContent(request.content());
        return content;
    }

    /**
     * 作者填写的摘要；没填时截取正文开头。
     */
    private static String summaryOf(ArticleRequest request) {
        if (request.summary() != null && !request.summary().isBlank()) {
            return request.summary();
        }
        return Texts.head(request.content().strip(), AUTO_SUMMARY_LENGTH);
    }

    private static List<Long> tagIdsOf(ArticleRequest request) {
        return request.tagIds() == null ? List.of() : request.tagIds().stream().distinct().toList();
    }

    @Override
    @Transactional
    public Article create(long authorId, ArticleRequest request) {
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setAuthorId(authorId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setVersion(0);
        save(article);
        articleBloomFilter.add(article.getId());

        articleContentService.save(contentOf(article.getId(), request));
        articleTagService.replaceTags(article.getId(), tagIds);
        return article;
    }

    @Override
    @Transactional
    public Article update(long id, long userId, int version, ArticleRequest request) {
        getOwned(id, userId);
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setId(id);
        article.setVersion(version);
        updateOrConflict(article);

        articleContentService.updateById(contentOf(id, request));
        articleTagService.replaceTags(id, tagIds);
        evictCache(id);
        eventPublisher.publish(new ArticleUpdatedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public Article publish(long id, long userId) {
        Article article = getOwned(id, userId);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            return article;
        }
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(DateTimes.now());
        updateOrConflict(article);
        counterApi.increment(CounterMetric.USER_ARTICLE, userId, 1);
        evictCache(id);
        eventPublisher.publish(new ArticlePublishedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public void delete(long id, long userId) {
        Article article = getOwned(id, userId);
        // 带上读到的版本号：期间被发布或删除时按冲突处理，保证按读到的状态增减文章数是正确的。
        // 乐观锁插件比对版本号并把它 +1，搜索同步以此让删除覆盖此前的写入
        Article deletion = new Article();
        deletion.setVersion(article.getVersion());
        boolean deleted = lambdaUpdate()
                .set(Article::getDeleted, 1)
                .eq(Article::getId, id)
                .update(deletion);
        if (!deleted) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.USER_ARTICLE, userId, -1);
        }
        evictCache(id);
        eventPublisher.publish(new ArticleDeletedEvent(id, userId));
    }

    @Override
    public void evictCache(long id) {
        detailCache.evict(id);
        briefCache.evict(id);
    }

    @Override
    public ArticleDetailVO getDetail(long id, Long viewerId) {
        CachedArticleDetail detail = detailCache.get(id, this::loadDetail);
        if (detail == null) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        Article article = detail.article();
        if (article.getStatus() == ArticleStatus.DRAFT && !Objects.equals(article.getAuthorId(), viewerId)) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.ARTICLE_VIEW, id, 1);
        }
        return articleConverter.toDetailVO(
                article,
                detail.content(),
                detail.category(),
                detail.tags(),
                userApi.getBriefs(List.of(article.getAuthorId())).get(article.getAuthorId()),
                countsOf(id));
    }

    @Override
    public PageResult<ArticleItem> pageLatest(Long categoryId, Long tagId, long page, long size) {
        Page<Article> result = latestPublished(categoryId, tagId).page(new Page<>(page, size));
        return new PageResult<>(toItems(toBriefs(result.getRecords())), result.getTotal(), page, size);
    }

    @Override
    public Article getIncludingDeleted(long id) {
        return baseMapper.selectByIdIncludingDeleted(id);
    }

    @Override
    public List<Article> listPublishedAfter(Long afterId, int limit) {
        return lambdaQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .gt(afterId != null, Article::getId, afterId)
                .orderByAsc(Article::getId)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    public List<Long> listIdsAfter(Long afterId, int limit) {
        return lambdaQuery()
                .select(Article::getId)
                .gt(afterId != null, Article::getId, afterId)
                .orderByAsc(Article::getId)
                .last("LIMIT " + limit)
                .list()
                .stream()
                .map(Article::getId)
                .toList();
    }

    @Override
    public List<Article> listUpdatedSinceIncludingDeleted(LocalDateTime since, Long afterId, int limit) {
        return baseMapper.selectUpdatedSinceIncludingDeleted(since, afterId == null ? 0 : afterId, limit);
    }

    @Override
    public List<Article> listPublishedSince(LocalDateTime since) {
        return lambdaQuery()
                .select(Article::getId, Article::getPublishedAt)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .ge(Article::getPublishedAt, since)
                .list();
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> ids) {
        return briefCache.getAll(ids, missing -> listByIds(missing).stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity())));
    }

    @Override
    public List<ArticleItem> listPublishedItems(List<Long> ids) {
        Map<Long, ArticleBrief> briefs = getBriefs(ids);
        return toItems(ids.stream()
                .map(briefs::get)
                .filter(brief -> brief != null && brief.status() == ArticleStatus.PUBLISHED)
                .toList());
    }

    @Override
    public CursorResult<ArticleItem> listPublishedByAuthor(long authorId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + (size + 1))
                .list(), size);
    }

    @Override
    public CursorResult<ArticleItem> listDrafts(long authorId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, ArticleStatus.DRAFT)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + (size + 1))
                .list(), size);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.USER_ARTICLE);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return baseMapper.countPublishedByAuthor(afterId, limit);
    }

    /**
     * 已发布文章的查询，可按分类、标签筛选，按发布时间倒序、同一秒发布的按 ID 倒序。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    private LambdaQueryChainWrapper<Article> latestPublished(Long categoryId, Long tagId) {
        return lambdaQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                // 经由 article_tag 的反向索引 idx_tag_article 找出文章；tagId 是 Long，拼接不会注入
                .inSql(tagId != null, Article::getId, "SELECT article_id FROM article_tag WHERE tag_id = " + tagId)
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId);
    }

    /**
     * 多查了一条的结果转为游标分页：多出的那条只用来判断是否还有下一页。
     */
    private CursorResult<ArticleItem> toCursorResult(List<Article> fetched, int size) {
        boolean hasMore = fetched.size() > size;
        List<Article> page = hasMore ? fetched.subList(0, size) : fetched;
        return new CursorResult<>(toItems(toBriefs(page)), hasMore ? page.getLast().getId() : null, hasMore);
    }

    private List<ArticleBrief> toBriefs(List<Article> articles) {
        return articles.stream().map(articleConverter::toBrief).toList();
    }

    /**
     * 批量补全分类、作者与计数，保持传入顺序。
     */
    private List<ArticleItem> toItems(List<ArticleBrief> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        Map<Long, CategoryBrief> categories = categoryService.listByIds(
                        articles.stream().map(ArticleBrief::categoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(Category::getId, categoryConverter::toBrief));
        Map<Long, UserBrief> authors =
                userApi.getBriefs(articles.stream().map(ArticleBrief::authorId).distinct().toList());
        Map<Long, Counts> counts =
                counterApi.get(CounterTarget.ARTICLE, articles.stream().map(ArticleBrief::id).toList());
        return articles.stream()
                .map(article -> articleConverter.toItem(
                        article,
                        categories.get(article.categoryId()),
                        authors.get(article.authorId()),
                        countsOf(counts.get(article.id()))))
                .toList();
    }

    /**
     * 从数据库加载详情中可缓存的部分。
     *
     * @return 文章不存在或已删除时为 null
     */
    private CachedArticleDetail loadDetail(long id) {
        Article article = getById(id);
        if (article == null) {
            return null;
        }
        List<Tag> tags = tagService.listInOrder(articleTagService.listTagIds(id));
        return new CachedArticleDetail(
                article,
                articleContentService.getById(id).getContent(),
                categoryConverter.toBrief(categoryService.getById(article.getCategoryId())),
                tagConverter.toVOs(tags));
    }

    /**
     * 查询当前用户自己的文章，用于写操作前的归属检查。
     *
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}、{@link CommonErrorCode#FORBIDDEN}
     */
    private Article getOwned(long id, long userId) {
        Article article = getById(id);
        if (article == null) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getAuthorId() != userId) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        return article;
    }

    /**
     * 按 ID 更新并比对版本号，成功后 article 中的版本号已是新值。
     *
     * @throws BizException {@link ArticleErrorCode#VERSION_CONFLICT}
     */
    private void updateOrConflict(Article article) {
        if (!updateById(article)) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * @throws BizException {@link ArticleErrorCode#CATEGORY_NOT_FOUND}、{@link ArticleErrorCode#TAG_NOT_FOUND}
     */
    private void checkCategoryAndTags(long categoryId, List<Long> tagIds) {
        if (categoryService.getById(categoryId) == null) {
            throw new BizException(ArticleErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!tagIds.isEmpty() && tagService.lambdaQuery().in(Tag::getId, tagIds).count() < tagIds.size()) {
            throw new BizException(ArticleErrorCode.TAG_NOT_FOUND);
        }
    }

    private ArticleCounts countsOf(long id) {
        return countsOf(counterApi.get(CounterTarget.ARTICLE, List.of(id)).get(id));
    }
}
