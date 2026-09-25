package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.service.HotArticleService;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "热榜")
@RestController
@RequestMapping("/hot-articles")
@RequiredArgsConstructor
public class HotArticleController {

    private final HotArticleService hotArticleService;

    @SaIgnore
    @Operation(summary = "热榜", description = "综合互动与发布时间的 Top 100，约每 5 分钟更新；每页 20 条，最多 5 页")
    @GetMapping
    public Result<PageResult<ArticleItemVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) @Max(HotArticleService.MAX_PAGE) int page) {
        return Result.ok(hotArticleService.page(page));
    }
}
