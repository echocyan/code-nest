package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.ArticleTag;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文章与标签的关联。
 */
public interface ArticleTagService extends IService<ArticleTag> {

    /**
     * 文章打的全部标签 ID。
     */
    List<Long> listTagIds(long articleId);

    /**
     * 一批文章各自打的标签 ID，按标签 ID 正序。
     *
     * @return 以文章 ID 为 key；没有标签的文章不出现在结果中
     */
    Map<Long, List<Long>> listTagIds(Collection<Long> articleIds);

    /**
     * 用给定标签整体替换文章原有的标签。
     */
    void replaceTags(long articleId, List<Long> tagIds);
}
