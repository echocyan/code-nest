package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.common.exception.BizException;

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
     * 整体替换文章内容，状态和发布时间不变。
     *
     * @param version 客户端读到的版本号，与数据库不一致时视为冲突
     * @throws BizException 文章不存在、不是作者本人、版本冲突、分类或标签不存在
     */
    Article update(long id, long userId, int version, ArticleRequest request);

    /**
     * 发布草稿，作者文章数 +1。已发布的文章重复发布不产生变化。
     *
     * @throws BizException 文章不存在、不是作者本人、版本冲突
     */
    Article publish(long id, long userId);

    /**
     * 软删除文章；删除的是已发布文章时，作者文章数 -1。
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
}
