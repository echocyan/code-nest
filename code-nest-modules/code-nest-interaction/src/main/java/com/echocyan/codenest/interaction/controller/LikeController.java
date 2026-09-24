package com.echocyan.codenest.interaction.controller;

import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.interaction.service.ArticleLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "点赞")
@RestController
@RequestMapping("/articles/{id}/like")
@RequiredArgsConstructor
public class LikeController {

    private final ArticleLikeService articleLikeService;

    @Operation(summary = "点赞", description = "已点过赞时不产生变化；只能对已发布的文章点赞")
    @PutMapping
    public Result<Void> like(@PathVariable long id) {
        articleLikeService.like(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消点赞", description = "没点过赞时不产生变化")
    @DeleteMapping
    public Result<Void> unlike(@PathVariable long id) {
        articleLikeService.unlike(AuthContext.currentUserId(), id);
        return Result.ok();
    }
}
