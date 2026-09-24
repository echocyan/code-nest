package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.Tag;
import java.util.Collection;
import java.util.List;

/**
 * 预置的文章标签。
 */
public interface TagService extends IService<Tag> {

    /**
     * 按 ID 顺序列出全部标签。
     */
    List<Tag> listInOrder();

    /**
     * 按 ID 顺序列出给定的标签，不存在的 ID 被忽略。
     */
    List<Tag> listInOrder(Collection<Long> ids);
}
