package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.entity.Category;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface CategoryConverter {

    CategoryBrief toBrief(Category category);

    List<CategoryBrief> toBriefs(List<Category> categories);
}
