package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章的写作、发布与删除。读取见 {@link ArticleReader}。
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
     * 查询已发布的文章。直接读数据库、不经缓存，供写操作做前置检查。
     *
     * @return 文章不存在、已删除或是草稿时为空
     */
    Optional<Article> findPublished(long id);

    /**
     * 按 ID 查询，已删除的文章也返回。
     *
     * @return 文章从未存在时为 null
     */
    Article getIncludingDeleted(long id);

    /**
     * 一批 {@code updated_at} 不早于 since 的文章，草稿和已删除的文章也返回，按文章 ID 正序。
     *
     * @param afterId 只返回 ID 大于它的文章；为 null 时从头开始
     */
    List<Article> listUpdatedSinceIncludingDeleted(LocalDateTime since, Long afterId, int limit);
}
