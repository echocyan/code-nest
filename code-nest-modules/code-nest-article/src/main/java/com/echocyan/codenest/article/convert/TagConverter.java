package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.TagVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface TagConverter {

    TagVO toVO(Tag tag);

    List<TagVO> toVOs(List<Tag> tags);
}
