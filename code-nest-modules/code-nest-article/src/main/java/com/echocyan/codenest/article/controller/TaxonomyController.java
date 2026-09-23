package com.echocyan.codenest.article.controller;

import com.echocyan.codenest.article.convert.TaxonomyConverter;
import com.echocyan.codenest.article.service.TaxonomyService;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "分类与标签")
@RestController
@RequiredArgsConstructor
public class TaxonomyController {

    private final TaxonomyService taxonomyService;
    private final TaxonomyConverter taxonomyConverter;

    @Operation(summary = "全部分类", description = "按预置顺序返回")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.ok(taxonomyConverter.toCategoryVOs(taxonomyService.listCategories()));
    }

    @Operation(summary = "全部标签")
    @GetMapping("/tags")
    public Result<List<TagVO>> listTags() {
        return Result.ok(taxonomyConverter.toTagVOs(taxonomyService.listTags()));
    }
}
