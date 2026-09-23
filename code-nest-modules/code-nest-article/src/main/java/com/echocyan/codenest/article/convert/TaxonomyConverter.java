package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.article.vo.TagVO;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface TaxonomyConverter {

    CategoryVO toVO(Category category);

    List<CategoryVO> toCategoryVOs(List<Category> categories);

    TagVO toVO(Tag tag);

    List<TagVO> toTagVOs(List<Tag> tags);
}
