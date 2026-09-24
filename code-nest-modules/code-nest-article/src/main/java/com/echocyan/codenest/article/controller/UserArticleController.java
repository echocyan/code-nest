package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 挂在用户下的文章列表。
 */
@Tag(name = "文章")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserArticleController {

    private final ArticleService articleService;

    @Operation(summary = "我的草稿", description = "按文章 ID 倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/me/drafts")
    public Result<CursorResult<ArticleItemVO>> myDrafts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(articleService.listDrafts(AuthContext.currentUserId(), cursor, size));
    }

    @SaIgnore
    @Operation(summary = "作者的文章", description = "只含已发布文章，按文章 ID 倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/{id}/articles")
    public Result<CursorResult<ArticleItemVO>> published(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(articleService.listPublishedByAuthor(id, cursor, size));
    }
}
