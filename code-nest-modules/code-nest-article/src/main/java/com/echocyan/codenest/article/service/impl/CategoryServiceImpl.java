package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.mapper.CategoryMapper;
import com.echocyan.codenest.article.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> listInOrder() {
        return lambdaQuery().orderByAsc(Category::getSort).orderByAsc(Category::getId).list();
    }
}
