package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.TagMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 预置的文章标签。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    /**
     * 按 ID 顺序列出全部标签。
     */
    public List<Tag> list() {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getId));
    }
}
