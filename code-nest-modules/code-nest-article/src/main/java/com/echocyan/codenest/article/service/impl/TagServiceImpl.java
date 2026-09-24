package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.TagMapper;
import com.echocyan.codenest.article.service.TagService;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Override
    public List<Tag> listInOrder() {
        return lambdaQuery().orderByAsc(Tag::getId).list();
    }

    @Override
    public List<Tag> listInOrder(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery().in(Tag::getId, ids).orderByAsc(Tag::getId).list();
    }
}
