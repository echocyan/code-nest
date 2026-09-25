package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.dto.CommentRequest;
import com.echocyan.codenest.article.dto.ReplyRequest;
import com.echocyan.codenest.article.service.CommentService;
import com.echocyan.codenest.article.vo.CommentIdVO;
import com.echocyan.codenest.article.vo.CommentVO;
import com.echocyan.codenest.article.vo.ReplyVO;
import com.echocyan.codenest.common.result.CursorResult;
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

@Tag(name = "评论")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "发表评论", description = "只能评论已发布的文章")
    @RateLimit(key = "comment-per-minute", limit = "${rate-limit.limits.comment-per-minute}", window = "1m",
            dimension = USER)
    @RateLimit(key = "comment-per-day", limit = "${rate-limit.limits.comment-per-day}", window = "1d", dimension = USER)
    @PostMapping("/articles/{id}/comments")
    public Result<CommentIdVO> comment(@PathVariable long id, @Valid @RequestBody CommentRequest request) {
        return Result.ok(new CommentIdVO(
                commentService.comment(id, AuthContext.currentUserId(), request.content()).getId()));
    }

    @SaIgnore
    @Operation(summary = "评论列表", description = "按时间倒序，每条带回复数，不内嵌回复")
    @GetMapping("/articles/{id}/comments")
    public Result<CursorResult<CommentVO>> listComments(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(commentService.listComments(id, cursor, size));
    }

    @Operation(summary = "发表回复", description = "id 可以是评论或回复；对回复再回复时，新回复仍挂在同一条评论下。"
            + "replyToUserId 须是该评论或同一评论下某条回复的作者")
    @RateLimit(key = "comment-per-minute", limit = "${rate-limit.limits.comment-per-minute}", window = "1m",
            dimension = USER)
    @RateLimit(key = "comment-per-day", limit = "${rate-limit.limits.comment-per-day}", window = "1d", dimension = USER)
    @PostMapping("/comments/{id}/replies")
    public Result<CommentIdVO> reply(@PathVariable long id, @Valid @RequestBody ReplyRequest request) {
        return Result.ok(new CommentIdVO(commentService.reply(
                id, AuthContext.currentUserId(), request.content(), request.replyToUserId()).getId()));
    }

    @SaIgnore
    @Operation(summary = "回复列表", description = "平铺，按时间正序，每条带被回复人的简要信息")
    @GetMapping("/comments/{id}/replies")
    public Result<CursorResult<ReplyVO>> listReplies(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(commentService.listReplies(id, cursor, size));
    }

    @Operation(summary = "删除评论或回复", description = "只能删除自己的；评论下的回复保留")
    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable long id) {
        commentService.delete(id, AuthContext.currentUserId());
        return Result.ok();
    }
}
