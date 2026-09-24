package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.mapper.CategoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 预置的文章分类。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 按预置顺序列出全部分类。
     */
    public List<Category> list() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }
}
