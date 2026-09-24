package com.echocyan.codenest.article.controller;

import com.echocyan.codenest.article.convert.TagConverter;
import com.echocyan.codenest.article.service.TagService;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 类名 Tag 与本模块的实体重名，这里用全限定名
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签")
@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final TagConverter tagConverter;

    @Operation(summary = "全部标签")
    @GetMapping("/tags")
    public Result<List<TagVO>> list() {
        return Result.ok(tagConverter.toVOs(tagService.list()));
    }
}
