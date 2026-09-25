package com.echocyan.codenest.interaction.controller;

import static com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension.USER;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.interaction.service.FavoriteService;
import com.echocyan.codenest.interaction.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "收藏")
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏", description = "已收藏过时不产生变化；只能收藏已发布的文章")
    @RateLimit(key = "like-favorite-per-minute", limit = "${rate-limit.limits.like-favorite-per-minute}", window = "1m",
            dimension = USER)
    @PutMapping("/articles/{id}/favorite")
    public Result<Void> favorite(@PathVariable long id) {
        favoriteService.favorite(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消收藏", description = "没收藏过时不产生变化")
    @RateLimit(key = "like-favorite-per-minute", limit = "${rate-limit.limits.like-favorite-per-minute}", window = "1m",
            dimension = USER)
    @DeleteMapping("/articles/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable long id) {
        favoriteService.unfavorite(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "我的收藏", description = "按收藏时间倒序；已删除的文章不出现，因此一页可能不足 size 条，以 hasMore 判断是否翻完")
    @GetMapping("/users/me/favorites")
    public Result<CursorResult<FavoriteVO>> listMine(@RequestParam(required = false) Long cursor,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(favoriteService.listMine(AuthContext.currentUserId(), cursor, size));
    }
}
