package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.article.vo.ArticleVersionVO;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension.USER;

@Tag(name = "文章")
@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    private static ArticleVersionVO versionOf(Article article) {
        return new ArticleVersionVO(article.getId(), article.getVersion());
    }

    @Operation(summary = "新建草稿")
    @PostMapping
    public Result<ArticleVersionVO> create(@Valid @RequestBody ArticleRequest request) {
        return Result.ok(versionOf(articleService.create(AuthContext.currentUserId(), request)));
    }

    @SaIgnore
    @Operation(summary = "最新文章", description = "只含已发布文章，按发布时间倒序；可按分类、标签筛选")
    @GetMapping
    public Result<PageResult<ArticleItemVO>> latest(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.ok(articleService.pageLatest(categoryId, tagId, page, size));
    }

    @Operation(summary = "编辑", description = "整体替换内容；version 为读到的版本号，已在别处修改时返回 409")
    @PutMapping("/{id}")
    public Result<ArticleVersionVO> update(@PathVariable long id, @RequestParam int version,
                                           @Valid @RequestBody ArticleRequest request) {
        return Result.ok(versionOf(articleService.update(id, AuthContext.currentUserId(), version, request)));
    }

    @Operation(summary = "发布", description = "已发布的文章重复发布不产生变化")
    @RateLimit(key = "publish-per-hour", limit = "${rate-limit.limits.publish-per-hour}", window = "1h",
            dimension = USER)
    @PostMapping("/{id}/publish")
    public Result<ArticleVersionVO> publish(@PathVariable long id) {
        return Result.ok(versionOf(articleService.publish(id, AuthContext.currentUserId())));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        articleService.delete(id, AuthContext.currentUserId());
        return Result.ok();
    }

    @SaIgnore
    @Operation(summary = "文章详情", description = "草稿只有作者本人能看到，其他人得到 404")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> get(@PathVariable long id) {
        return Result.ok(articleService.getDetail(id, AuthContext.currentUserIdOrNull()));
    }
}
