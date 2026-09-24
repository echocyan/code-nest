package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.TagVO;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface TagConverter {

    TagVO toVO(Tag tag);

    List<TagVO> toVOs(List<Tag> tags);
}
