package com.echocyan.codenest.article.api;

import com.echocyan.codenest.common.result.PageResult;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * article 模块对其他模块的门面。除 {@link #findSnapshot} 外，已删除的文章、评论视为不存在。
 */
public interface ArticleApi {

    /**
     * 查询文章的状态与作者。
     *
     * @return 文章不存在时为空
     */
    Optional<ArticleState> findState(long articleId);

    /**
     * 批量查询文章摘要，草稿也会返回，由调用方按状态过滤。经缓存读取，文章编辑、发布、删除后失效。
     *
     * @return 以文章 ID 为 key；不存在的文章不出现在结果中
     */
    Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds);

    /**
     * 一批作者已发布的文章，按文章 ID 倒序（雪花 ID，约等于创建时间倒序），以文章 ID 作游标。
     *
     * @param cursor 只返回 ID 小于它的文章；为 null 时从最新的开始
     * @param limit  最多返回的条数
     */
    List<ArticleBrief> listByAuthors(Collection<Long> authorIds, Long cursor, int limit);

    /**
     * 按关键词搜索已发布的文章：标题、摘要、正文任一包含关键词（{@code LIKE '%kw%'}，不分词）即命中，
     * 按发布时间倒序，页码分页。关键词中的 {@code %}、{@code _} 按字面匹配。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    PageResult<ArticleBrief> searchPublished(String keyword, Long categoryId, Long tagId, long page, long size);

    /**
     * 查询文章当前的完整内容与版本号，已删除的文章也会返回。
     *
     * @return 文章从未存在时为空
     */
    Optional<ArticleSnapshot> findSnapshot(long articleId);

    /**
     * 一批已发布文章的完整内容，按文章 ID 正序，以文章 ID 作游标，用于全量导入搜索索引。
     *
     * @param afterId 只返回 ID 大于它的文章；为 null 时从头开始
     * @param limit   最多返回的条数
     */
    List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit);

    /**
     * 一批在给定时间及之后更新过的文章的完整内容，草稿和已删除的文章也返回，按文章 ID 正序，以文章 ID 作游标。
     * 用于重建搜索索引后追补重建期间的变更。
     *
     * @param since   只返回 {@code updated_at} 不早于它的文章
     * @param afterId 只返回 ID 大于它的文章；为 null 时从头开始
     * @param limit   最多返回的条数
     */
    List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit);

    /**
     * 发布时间不早于 since 的已发布文章，作为热榜的候选集。
     *
     * @return 以文章 ID 为 key，发布时间为 value
     */
    Map<Long, LocalDateTime> getPublishedSince(LocalDateTime since);

    /**
     * 批量查询评论或回复的摘要，不检查所属文章的状态。
     *
     * @return 以评论 ID 为 key；不存在的评论不出现在结果中
     */
    Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds);
}
