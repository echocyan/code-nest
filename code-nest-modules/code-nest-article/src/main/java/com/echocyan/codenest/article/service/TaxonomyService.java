package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.CategoryMapper;
import com.echocyan.codenest.article.mapper.TagMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 预置的分类与标签。
 */
@Service
@RequiredArgsConstructor
public class TaxonomyService {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public List<Category> listCategories() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    public List<Tag> listTags() {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getId));
    }
}
