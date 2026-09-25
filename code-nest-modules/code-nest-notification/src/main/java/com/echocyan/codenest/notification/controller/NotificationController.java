package com.echocyan.codenest.notification.controller;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通知")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知", description = "按时间倒序；cursor 为上一页返回的 nextCursor；打开列表不会标记已读")
    @GetMapping
    public Result<CursorResult<NotificationVO>> listMine(@RequestParam(required = false) Long cursor,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(notificationService.listMine(AuthContext.currentUserId(), cursor, size));
    }

    @Operation(summary = "未读数", description = "最多数到 " + NotificationService.MAX_UNREAD_COUNT + "，前端显示为 99+")
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.ok(notificationService.countUnread(AuthContext.currentUserId()));
    }

    @Operation(summary = "标记单条已读", description = "只能标记自己的通知，否则按不存在处理")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        notificationService.markRead(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(AuthContext.currentUserId());
        return Result.ok();
    }
}
