package com.echocyan.codenest.notification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.notification.NotificationErrorCode;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.mapper.NotificationMapper;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.notification.vo.NotificationVO;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /** 文章、评论或回复已被删除时显示的内容。 */
    private static final String DELETED_CONTENT = "该内容已删除";

    private final UserApi userApi;
    private final ArticleApi articleApi;

    @Override
    public void send(Notification notification) {
        if (notification.getActorId().equals(notification.getRecipientId())) {
            return;
        }
        LocalDateTime now = DateTimes.now();
        notification.setId(IdWorker.getId());
        notification.setIsRead(false);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        baseMapper.insertIgnore(notification);
    }

    @Override
    public CursorResult<NotificationVO> listMine(long recipientId, Long cursor, int size) {
        // 多取一条判断是否还有下一页
        List<Notification> fetched = lambdaQuery()
                .eq(Notification::getRecipientId, recipientId)
                .lt(cursor != null, Notification::getId, cursor)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + (size + 1))
                .list();
        boolean hasMore = fetched.size() > size;
        List<Notification> page = hasMore ? fetched.subList(0, size) : fetched;
        if (page.isEmpty()) {
            return CursorResult.empty();
        }
        Map<Long, UserBrief> actors = userApi.getBriefs(ids(page, Notification::getActorId));
        Map<Long, ArticleBrief> articles = articleApi.getBriefs(ids(page, Notification::getArticleId));
        Map<Long, CommentBrief> comments = articleApi.getCommentBriefs(ids(page, Notification::getCommentId));
        List<NotificationVO> list = page.stream()
                .map(notification -> toVO(notification, actors, articles, comments))
                .toList();
        return new CursorResult<>(list, hasMore ? page.getLast().getId() : null, hasMore);
    }

    @Override
    public int countUnread(long recipientId) {
        return baseMapper.countUnread(recipientId, MAX_UNREAD_COUNT);
    }

    @Override
    public void markRead(long recipientId, long notificationId) {
        // updated_at 每次都会刷新，所以已读的通知再标一次也能更新到一行
        boolean updated = lambdaUpdate()
                .set(Notification::getIsRead, true)
                .eq(Notification::getId, notificationId)
                .eq(Notification::getRecipientId, recipientId)
                .update(new Notification());
        if (!updated) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Override
    public void markAllRead(long recipientId) {
        lambdaUpdate()
                .set(Notification::getIsRead, true)
                .eq(Notification::getRecipientId, recipientId)
                .eq(Notification::getIsRead, false)
                .update(new Notification());
    }

    /**
     * 文章已删除时，它下面的评论也按已删除展示。
     */
    private static NotificationVO toVO(Notification notification, Map<Long, UserBrief> actors,
                                       Map<Long, ArticleBrief> articles, Map<Long, CommentBrief> comments) {
        ArticleBrief article = null;
        String articleTitle = null;
        if (notification.getArticleId() != null) {
            article = articles.get(notification.getArticleId());
            articleTitle = article == null ? DELETED_CONTENT : article.title();
        }
        String commentSummary = null;
        if (notification.getCommentId() != null) {
            CommentBrief comment = comments.get(notification.getCommentId());
            commentSummary = article == null || comment == null ? DELETED_CONTENT : comment.summary();
        }
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                actors.get(notification.getActorId()),
                notification.getArticleId(),
                articleTitle,
                notification.getCommentId(),
                commentSummary,
                notification.getIsRead(),
                notification.getCreatedAt());
    }

    /**
     * 本页通知里某一列非空的 ID，去重。
     */
    private static List<Long> ids(List<Notification> page, Function<Notification, Long> column) {
        return page.stream().map(column).filter(Objects::nonNull).distinct().toList();
    }
}
