package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.vo.CategoryVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface CategoryConverter {

    CategoryVO toVO(Category category);

    List<CategoryVO> toVOs(List<Category> categories);
}
