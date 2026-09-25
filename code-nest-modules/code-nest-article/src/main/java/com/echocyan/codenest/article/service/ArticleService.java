package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.PageResult;
import java.util.Collection;
import java.util.List;

/**
 * 文章的写作、发布、删除与详情。
 */
public interface ArticleService extends IService<Article> {

    /**
     * 新建草稿。
     *
     * @throws BizException 分类或标签不存在
     */
    Article create(long authorId, ArticleRequest request);

    /**
     * 整体替换文章内容，状态和发布时间不变，版本号 +1，并发出 {@code article.updated}。
     *
     * @param version 客户端读到的版本号，与数据库不一致时视为冲突
     * @throws BizException 文章不存在、不是作者本人、版本冲突、分类或标签不存在
     */
    Article update(long id, long userId, int version, ArticleRequest request);

    /**
     * 发布草稿，版本号 +1，作者文章数 +1，并发出 {@code article.published}。已发布的文章重复发布不产生变化。
     *
     * @throws BizException 文章不存在、不是作者本人、版本冲突
     */
    Article publish(long id, long userId);

    /**
     * 软删除文章，版本号 +1，并发出 {@code article.deleted}；删除的是已发布文章时，作者文章数 -1。
     *
     * @throws BizException 文章不存在、不是作者本人、并发修改导致版本冲突
     */
    void delete(long id, long userId);

    /**
     * 文章详情。草稿只有作者本人能看到，对其他人表现为不存在；已发布的文章每次查看浏览量 +1。
     *
     * @param viewerId 当前访客，匿名时为 null
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    ArticleDetailVO getDetail(long id, Long viewerId);

    /**
     * 最新发布的文章，按发布时间倒序，页码分页。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    PageResult<ArticleItemVO> pageLatest(Long categoryId, Long tagId, long page, long size);

    /**
     * 按关键词搜索已发布的文章，见 {@link com.echocyan.codenest.article.api.ArticleApi#searchPublished}。
     */
    PageResult<Article> searchPublished(String keyword, Long categoryId, Long tagId, long page, long size);

    /**
     * 按 ID 查询，已删除的文章也返回。
     *
     * @return 文章从未存在时为 null
     */
    Article getIncludingDeleted(long id);

    /**
     * 一批已发布的文章，按文章 ID 正序。
     *
     * @param afterId 只返回 ID 大于它的文章；为 null 时从头开始
     */
    List<Article> listPublishedAfter(Long afterId, int limit);

    /**
     * 一批作者已发布的文章，按文章 ID 倒序。
     *
     * @param cursor 只返回 ID 小于它的文章；为 null 时从最新的开始
     */
    List<Article> listPublishedByAuthors(Collection<Long> authorIds, Long cursor, int limit);

    /**
     * 某位作者已发布的文章，按文章 ID 倒序，游标分页。
     */
    CursorResult<ArticleItemVO> listPublishedByAuthor(long authorId, Long cursor, int size);

    /**
     * 作者自己的草稿，按文章 ID 倒序，游标分页。
     */
    CursorResult<ArticleItemVO> listDrafts(long authorId, Long cursor, int size);
}
