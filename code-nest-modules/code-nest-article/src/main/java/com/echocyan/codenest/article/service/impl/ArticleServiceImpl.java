package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.convert.TagConverter;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import com.echocyan.codenest.article.service.ArticleContentService;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.ArticleTagService;
import com.echocyan.codenest.article.service.CategoryService;
import com.echocyan.codenest.article.service.TagService;
import com.echocyan.codenest.article.vo.ArticleCountsVO;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /** 自动摘要截取的正文字符数。 */
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
        return article;
    }

    @Override
    @Transactional
    public void delete(long id, long userId) {
        Article article = getOwned(id, userId);
        // 带上读到的版本号：期间被发布或删除时按冲突处理，保证按读到的状态增减文章数是正确的
        boolean deleted = lambdaUpdate()
                .eq(Article::getId, id)
                .eq(Article::getVersion, article.getVersion())
                .remove();
        if (!deleted) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.USER_ARTICLE, userId, -1);
        }
    }

    @Override
    public ArticleDetailVO getDetail(long id, Long viewerId) {
        Article article = getById(id);
        if (article == null
                || article.getStatus() == ArticleStatus.DRAFT && !Objects.equals(article.getAuthorId(), viewerId)) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.ARTICLE_VIEW, id, 1);
        }
        List<Tag> tags = tagService.listInOrder(articleTagService.listTagIds(id));
        return articleConverter.toDetailVO(
                article,
                articleContentService.getById(id).getContent(),
                categoryConverter.toVO(categoryService.getById(article.getCategoryId())),
                tagConverter.toVOs(tags),
                userApi.getBriefs(List.of(article.getAuthorId())).get(article.getAuthorId()),
                countsOf(id));
    }

    @Override
    public PageResult<ArticleItemVO> pageLatest(Long categoryId, Long tagId, long page, long size) {
        Page<Article> result = latestPublished(categoryId, tagId).page(new Page<>(page, size));
        return new PageResult<>(toItems(result.getRecords()), result.getTotal(), page, size);
    }

    @Override
    public PageResult<Article> searchPublished(String keyword, Long categoryId, Long tagId, long page, long size) {
        // 转义 LIKE 通配符，MySQL 默认的转义字符是反斜杠
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Page<Article> result = latestPublished(categoryId, tagId)
                .and(match -> match
                        .like(Article::getTitle, escaped)
                        .or().like(Article::getSummary, escaped)
                        .or().apply("id IN (SELECT article_id FROM article_content WHERE content LIKE {0})",
                                "%" + escaped + "%"))
                .page(new Page<>(page, size));
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public List<Article> listPublishedByAuthors(Collection<Long> authorIds, Long cursor, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(Article::getAuthorId, authorIds)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    public CursorResult<ArticleItemVO> listPublishedByAuthor(long authorId, Long cursor, int size) {
        return toCursorResult(listPublishedByAuthors(List.of(authorId), cursor, size + 1), size);
    }

    @Override
    public CursorResult<ArticleItemVO> listDrafts(long authorId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, ArticleStatus.DRAFT)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + (size + 1))
                .list(), size);
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
    private CursorResult<ArticleItemVO> toCursorResult(List<Article> fetched, int size) {
        boolean hasMore = fetched.size() > size;
        List<Article> page = hasMore ? fetched.subList(0, size) : fetched;
        return new CursorResult<>(toItems(page), hasMore ? page.getLast().getId() : null, hasMore);
    }

    /**
     * 批量补全分类、作者与计数，保持传入顺序。
     */
    private List<ArticleItemVO> toItems(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        Map<Long, CategoryVO> categories = categoryService.listByIds(
                        articles.stream().map(Article::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(Category::getId, categoryConverter::toVO));
        Map<Long, UserBrief> authors = userApi.getBriefs(articles.stream().map(Article::getAuthorId).distinct().toList());
        Map<Long, Counts> counts = counterApi.get(CounterTarget.ARTICLE, articles.stream().map(Article::getId).toList());
        return articles.stream()
                .map(article -> articleConverter.toItemVO(
                        article,
                        categories.get(article.getCategoryId()),
                        authors.get(article.getAuthorId()),
                        countsVO(counts.get(article.getId()))))
                .toList();
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

    private ArticleCountsVO countsOf(long id) {
        return countsVO(counterApi.get(CounterTarget.ARTICLE, List.of(id)).get(id));
    }

    private static ArticleCountsVO countsVO(Counts counts) {
        return new ArticleCountsVO(
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
     * 作者填写的摘要；没填时截取正文开头。按码点截取，避免切开 emoji 等代理对。
     */
    private static String summaryOf(ArticleRequest request) {
        if (request.summary() != null && !request.summary().isBlank()) {
            return request.summary();
        }
        String content = request.content().strip();
        return content.substring(0, content.offsetByCodePoints(0,
                Math.min(AUTO_SUMMARY_LENGTH, content.codePointCount(0, content.length()))));
    }

    private static List<Long> tagIdsOf(ArticleRequest request) {
        return request.tagIds() == null ? List.of() : request.tagIds().stream().distinct().toList();
    }
}
