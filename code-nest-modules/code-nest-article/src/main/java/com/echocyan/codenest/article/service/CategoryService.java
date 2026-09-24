package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.Category;
import java.util.List;

/**
 * 预置的文章分类。
 */
public interface CategoryService extends IService<Category> {

    /**
     * 按预置顺序列出全部分类。
     */
    List<Category> listInOrder();
}
