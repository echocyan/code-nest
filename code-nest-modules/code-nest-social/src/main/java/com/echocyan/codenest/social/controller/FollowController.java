package com.echocyan.codenest.social.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.social.service.FollowService;
import com.echocyan.codenest.social.vo.FollowUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension.USER;

@Tag(name = "关注")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class FollowController {

    /**
     * 一次最多查询的用户数，与列表页的最大 size 一致。
     */
    private static final int MAX_IDS = 50;

    private final FollowService followService;

    @Operation(summary = "关注", description = "已关注时不产生变化")
    @RateLimit(key = "follow-per-minute", limit = "${rate-limit.limits.follow-per-minute}", window = "1m",
            dimension = USER)
    @PutMapping("/{id}/follow")
    public Result<Void> follow(@PathVariable long id) {
        followService.follow(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取关", description = "没关注过时不产生变化")
    @RateLimit(key = "follow-per-minute", limit = "${rate-limit.limits.follow-per-minute}", window = "1m",
            dimension = USER)
    @DeleteMapping("/{id}/follow")
    public Result<Void> unfollow(@PathVariable long id) {
        followService.unfollow(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @SaIgnore
    @Operation(summary = "粉丝列表", description = "按关注时间倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/{id}/followers")
    public Result<CursorResult<FollowUserVO>> followers(@PathVariable long id,
                                                        @RequestParam(required = false) Long cursor,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(followService.listFollowers(id, cursor, size));
    }

    @SaIgnore
    @Operation(summary = "关注列表", description = "按关注时间倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/{id}/followings")
    public Result<CursorResult<FollowUserVO>> followings(@PathVariable long id,
                                                         @RequestParam(required = false) Long cursor,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(followService.listFollowings(id, cursor, size));
    }

    @Operation(summary = "批量查询关注状态", description = "以用户 ID 为 key，值为是否已关注；ids 最多 " + MAX_IDS
            + " 个")
    @GetMapping("/follow-states")
    public Result<Map<Long, Boolean>> followStates(@RequestParam @Size(max = MAX_IDS) List<Long> ids) {
        Set<Long> followed = followService.listFollowedAuthorIds(AuthContext.currentUserId(), ids);
        Map<Long, Boolean> states = new LinkedHashMap<>();
        ids.forEach(id -> states.put(id, followed.contains(id)));
        return Result.ok(states);
    }
}
