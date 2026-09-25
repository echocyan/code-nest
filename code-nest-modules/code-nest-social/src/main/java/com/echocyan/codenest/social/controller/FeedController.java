package com.echocyan.codenest.social.controller;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.social.service.FeedService;
import com.echocyan.codenest.social.vo.FeedItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Feed")
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "关注 Feed", description = "我关注的作者已发布的文章，按文章 ID 倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping
    public Result<CursorResult<FeedItemVO>> feed(@RequestParam(required = false) Long cursor,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(feedService.read(AuthContext.currentUserId(), cursor, size));
    }
}
