package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.api.*;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.convert.CommentConverter;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章的全部读取：模块门面 {@link ArticleApi}，以及详情、最新文章、作者文章、草稿等模块内的读取。
 * <p>
 * 可见性规则、各种投影的组装与缓存的加载都在这里；写方只通过 {@link ArticleCache#added}、{@link ArticleCache#evict}
 * 通知变更。启动时（所有单例创建完后）如果布隆过滤器不存在，按全部未删除文章（含草稿）的 ID 重建。
 */
@Component
@RequiredArgsConstructor
public class ArticleReader implements ArticleApi, SmartInitializingSingleton {

    /**
     * 重建布隆过滤器时每批读取的文章 ID 数。
     */
    private static final int REBUILD_BATCH = 1000;

    private final ArticleService articleService;
    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;
    private final ArticleCache articleCache;
    private final ArticleConverter articleConverter;
    private final CategoryConverter categoryConverter;
    private final CommentConverter commentConverter;
    private final UserApi userApi;
    private final CounterApi counterApi;

    private static ArticleCounts countsOf(Counts counts) {
        return new ArticleCounts(
                counts.get(CounterMetric.ARTICLE_LIKE),
                counts.get(CounterMetric.ARTICLE_FAVORITE),
                counts.get(CounterMetric.ARTICLE_COMMENT),
                counts.get(CounterMetric.ARTICLE_VIEW));
    }

    @Override
    public Optional<ArticleState> findState(long articleId) {
        return Optional.ofNullable(articleService.getById(articleId)).map(articleConverter::toState);
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds) {
        return articleCache.getBriefs(articleIds, missing -> articleService.listByIds(missing).stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity())));
    }

    @Override
    public List<ArticleItem> listPublishedItems(List<Long> articleIds) {
        Map<Long, ArticleBrief> briefs = getBriefs(articleIds);
        return toItems(articleIds.stream()
                .map(briefs::get)
                .filter(brief -> brief != null && brief.status() == ArticleStatus.PUBLISHED)
                .toList());
    }

    @Override
    public Optional<ArticleSnapshot> findSnapshot(long articleId) {
        return Optional.ofNullable(articleService.getIncludingDeleted(articleId))
                .map(article -> toSnapshots(List.of(article)).getFirst());
    }

    @Override
    public List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit) {
        return toSnapshots(listPublishedAfter(afterId, limit));
    }

    @Override
    public List<ArticleState> listPublishedStates(Long afterId, int limit) {
        return listPublishedAfter(afterId, limit).stream().map(articleConverter::toState).toList();
    }

    @Override
    public List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit) {
        return toSnapshots(articleService.listUpdatedSinceIncludingDeleted(since, afterId, limit));
    }

    @Override
    public Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentService.listByIds(commentIds).stream()
                .map(commentConverter::toBrief)
                .collect(Collectors.toMap(CommentBrief::id, Function.identity()));
    }

    /**
     * 文章详情。草稿只有作者本人能看到，对其他人表现为不存在；已发布的文章每次查看浏览量 +1。
     * 元数据、正文、分类与标签经缓存读取，作者信息与计数每次另行组装。
     *
     * @param viewerId 当前访客，匿名时为 null
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    public ArticleDetailVO getDetail(long id, Long viewerId) {
        ArticleCache.Detail detail = articleCache.getDetail(id, this::loadDetail);
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
                countsOf(counterApi.get(CounterTarget.ARTICLE, List.of(id)).get(id)));
    }

    /**
     * 最新发布的文章，按发布时间倒序、同一秒发布的按 ID 倒序，页码分页。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    public PageResult<ArticleItem> pageLatest(Long categoryId, Long tagId, long page, long size) {
        Page<Article> result = articleService.lambdaQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                // 经由 article_tag 的反向索引 idx_tag_article 找出文章；tagId 是 Long，拼接不会注入
                .inSql(tagId != null, Article::getId, "SELECT article_id FROM article_tag WHERE tag_id = " + tagId)
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId)
                .page(new Page<>(page, size));
        return new PageResult<>(toItems(toBriefs(result.getRecords())), result.getTotal(), page, size);
    }

    /**
     * 某位作者已发布的文章，按文章 ID 倒序，游标分页。
     */
    public CursorResult<ArticleItem> listPublishedByAuthor(long authorId, Long cursor, int size) {
        return toCursorResult(authorArticles(authorId, ArticleStatus.PUBLISHED, cursor, size), size);
    }

    /**
     * 作者自己的草稿，按文章 ID 倒序，游标分页。
     */
    public CursorResult<ArticleItem> listDrafts(long authorId, Long cursor, int size) {
        return toCursorResult(authorArticles(authorId, ArticleStatus.DRAFT, cursor, size), size);
    }

    @Override
    public void afterSingletonsInstantiated() {
        articleCache.rebuildBloomFilterIfAbsent(this::listIdsAfter);
    }

    /**
     * 作者某一状态的文章，按 ID 倒序，多取一条用于判断是否还有下一页。
     */
    private List<Article> authorArticles(long authorId, ArticleStatus status, Long cursor, int size) {
        return articleService.lambdaQuery()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + (size + 1))
                .list();
    }

    /**
     * 一批已发布的文章，按文章 ID 正序。
     *
     * @param afterId 只返回 ID 大于它的文章；为 null 时从头开始
     */
    private List<Article> listPublishedAfter(Long afterId, int limit) {
        return idsAfter(articleService.lambdaQuery().eq(Article::getStatus, ArticleStatus.PUBLISHED), afterId, limit)
                .list();
    }

    /**
     * 一批未删除文章（含草稿）的 ID，按 ID 正序，用于重建布隆过滤器。
     *
     * @param afterId 只返回大于它的 ID；为 null 时从头开始
     */
    private List<Long> listIdsAfter(Long afterId) {
        return idsAfter(articleService.lambdaQuery().select(Article::getId), afterId, REBUILD_BATCH)
                .list()
                .stream()
                .map(Article::getId)
                .toList();
    }

    private static LambdaQueryChainWrapper<Article> idsAfter(LambdaQueryChainWrapper<Article> query, Long afterId,
                                                             int limit) {
        return query
                .gt(afterId != null, Article::getId, afterId)
                .orderByAsc(Article::getId)
                .last("LIMIT " + limit);
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
     * 批量补全正文与标签，组装成快照，保持传入顺序；已删除的文章也可以传入。
     */
    private List<ArticleSnapshot> toSnapshots(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, String> contents = articleContentService.listByIds(ids).stream()
                .collect(Collectors.toMap(ArticleContent::getArticleId, ArticleContent::getContent));
        Map<Long, List<Long>> tagIds = articleTagService.listTagIds(ids);
        Map<Long, String> tagNames = tagService.listInOrder(
                        tagIds.values().stream().flatMap(List::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return articles.stream()
                .map(article -> {
                    List<Long> ownTagIds = tagIds.getOrDefault(article.getId(), List.of());
                    return articleConverter.toSnapshot(article, contents.get(article.getId()), ownTagIds,
                            ownTagIds.stream().map(tagNames::get).toList());
                })
                .toList();
    }

    /**
     * 从数据库加载详情中可缓存的部分。
     *
     * @return 文章不存在或已删除时为 null
     */
    private ArticleCache.Detail loadDetail(long id) {
        Article article = articleService.getById(id);
        if (article == null) {
            return null;
        }
        ArticleSnapshot snapshot = toSnapshots(List.of(article)).getFirst();
        List<TagVO> tags = new ArrayList<>(snapshot.tagIds().size());
        for (int i = 0; i < snapshot.tagIds().size(); i++) {
            tags.add(new TagVO(snapshot.tagIds().get(i), snapshot.tagNames().get(i)));
        }
        return new ArticleCache.Detail(
                article,
                snapshot.content(),
                categoryConverter.toBrief(categoryService.getById(article.getCategoryId())),
                tags);
    }
}
