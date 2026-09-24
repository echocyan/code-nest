package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.ArticleTag;
import java.util.List;

/**
 * 文章与标签的关联。
 */
public interface ArticleTagService extends IService<ArticleTag> {

    /**
     * 文章打的全部标签 ID。
     */
    List<Long> listTagIds(long articleId);

    /**
     * 用给定标签整体替换文章原有的标签。
     */
    void replaceTags(long articleId, List<Long> tagIds);
}
