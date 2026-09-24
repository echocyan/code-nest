package com.echocyan.codenest.search.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.SearchService;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "搜索")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @SaIgnore
    @Operation(summary = "搜索文章", description = "只搜已发布文章；可按分类、标签筛选；page × size 超过 1000（即 from + size > 1000）时返回 400")
    @GetMapping("/search/articles")
    public Result<PageResult<SearchArticleVO>> search(
            @RequestParam @NotBlank String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "RELEVANCE") SearchSort sort,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.ok(searchService.search(q, categoryId, tagId, sort, page, size));
    }
}
