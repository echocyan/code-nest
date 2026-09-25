package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.article.mapper.CommentMapper;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.CommentService;
import com.echocyan.codenest.article.vo.CommentVO;
import com.echocyan.codenest.article.vo.ReplyVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.*;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService,
        CounterSource {

    /**
     * 已删除但仍有回复的评论显示的内容。
     */
    private static final String DELETED_CONTENT = "该评论已删除";

    private final ArticleService articleService;
    private final UserApi userApi;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;

    /**
     * 按多查的一条判断是否还有下一页，并以本页最后一条的 ID 作为下一页的游标。
     *
     * @param rows 按游标方向排好序、最多 size + 1 条
     */
    private static CursorResult<Comment> pageOf(List<Comment> rows, int size) {
        if (rows.size() <= size) {
            return new CursorResult<>(rows, null, false);
        }
        List<Comment> page = rows.subList(0, size);
        return new CursorResult<>(page, page.getLast().getId(), true);
    }

    @Override
    @Transactional
    public Comment comment(long articleId, long userId, String content) {
        Article article = requirePublished(articleId);
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setRootId(Comment.NO_ROOT);
        comment.setContent(content);
        save(comment);
        counterApi.increment(CounterMetric.ARTICLE_COMMENT, articleId, 1);
        eventPublisher.publish(new CommentCreatedEvent(comment.getId(), articleId, userId, Comment.NO_ROOT, null,
                article.getAuthorId()));
        return comment;
    }

    @Override
    public CursorResult<CommentVO> listComments(long articleId, Long cursor, int size) {
        requirePublished(articleId);
        CursorResult<Comment> page = pageOf(baseMapper.selectVisibleComments(
                articleId, cursor == null ? Long.MAX_VALUE : cursor, size + 1), size);
        return page.map(commentVOMapper(page.list()));
    }

    @Override
    @Transactional
    public Comment reply(long commentId, long userId, String content, Long replyToUserId) {
        Comment target = getById(commentId);
        if (target == null) {
            throw new BizException(ArticleErrorCode.COMMENT_NOT_FOUND);
        }
        Article article = requirePublished(target.getArticleId());
        long rootId = target.isReply() ? target.getRootId() : target.getId();
        if (replyToUserId == null && target.isReply()) {
            replyToUserId = target.getUserId();
        } else if (replyToUserId != null && !inDiscussion(target.getArticleId(), rootId, replyToUserId)) {
            throw new BizException(ArticleErrorCode.REPLY_TO_USER_INVALID);
        }
        Comment reply = new Comment();
        reply.setArticleId(target.getArticleId());
        reply.setUserId(userId);
        reply.setRootId(rootId);
        reply.setReplyToUserId(replyToUserId);
        reply.setContent(content);
        save(reply);
        counterApi.increment(CounterMetric.COMMENT_REPLY, rootId, 1);
        counterApi.increment(CounterMetric.ARTICLE_COMMENT, target.getArticleId(), 1);
        // 没有 @ 人时，被回复的是评论的作者
        eventPublisher.publish(new CommentCreatedEvent(reply.getId(), target.getArticleId(), userId, rootId,
                replyToUserId != null ? replyToUserId : target.getUserId(), article.getAuthorId()));
        return reply;
    }

    @Override
    public CursorResult<ReplyVO> listReplies(long commentId, Long cursor, int size) {
        Comment root = baseMapper.selectByIdIncludingDeleted(commentId);
        if (root == null || root.isReply()) {
            throw new BizException(ArticleErrorCode.COMMENT_NOT_FOUND);
        }
        requirePublished(root.getArticleId());
        CursorResult<Comment> page = pageOf(lambdaQuery()
                .eq(Comment::getArticleId, root.getArticleId())
                .eq(Comment::getRootId, commentId)
                .gt(cursor != null, Comment::getId, cursor)
                .orderByAsc(Comment::getId)
                .last("LIMIT " + (size + 1))
                .list(), size);
        Map<Long, UserBrief> users = userApi.getBriefs(page.list().stream()
                .flatMap(reply -> Stream.of(reply.getUserId(), reply.getReplyToUserId()))
                .filter(Objects::nonNull)
                .toList());
        return page.map(reply -> new ReplyVO(
                reply.getId(),
                reply.getRootId(),
                reply.getContent(),
                users.get(reply.getUserId()),
                reply.getReplyToUserId() == null ? null : users.get(reply.getReplyToUserId()),
                reply.getCreatedAt()));
    }

    @Override
    @Transactional
    public void delete(long id, long userId) {
        Comment comment = getById(id);
        if (comment == null) {
            throw new BizException(ArticleErrorCode.COMMENT_NOT_FOUND);
        }
        if (comment.getUserId() != userId) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        // 并发重复删除时只有一次能删到，计数不会多减
        if (!removeById(id)) {
            throw new BizException(ArticleErrorCode.COMMENT_NOT_FOUND);
        }
        if (comment.isReply()) {
            counterApi.increment(CounterMetric.COMMENT_REPLY, comment.getRootId(), -1);
        }
        counterApi.increment(CounterMetric.ARTICLE_COMMENT, comment.getArticleId(), -1);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.ARTICLE_COMMENT, CounterMetric.COMMENT_REPLY);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return switch (metric) {
            case ARTICLE_COMMENT -> baseMapper.countByArticle(afterId, limit);
            case COMMENT_REPLY -> baseMapper.countByRoot(afterId, limit);
            default -> throw new IllegalArgumentException("不负责的计数指标: " + metric);
        };
    }

    /**
     * 用户是否是该评论或其下某条回复的作者。
     */
    private boolean inDiscussion(long articleId, long rootId, long userId) {
        return lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .and(w -> w.eq(Comment::getId, rootId).or().eq(Comment::getRootId, rootId))
                .eq(Comment::getUserId, userId)
                .exists();
    }

    /**
     * @throws BizException 文章不存在、已删除或是草稿时 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    private Article requirePublished(long articleId) {
        Article article = articleService.getById(articleId);
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    private Function<Comment, CommentVO> commentVOMapper(List<Comment> comments) {
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        Map<Long, Counts> counts = counterApi.get(CounterTarget.COMMENT, ids);
        Map<Long, UserBrief> authors = userApi.getBriefs(comments.stream().map(Comment::getUserId).toList());
        return comment -> {
            boolean deleted = comment.getDeleted() != 0;
            return new CommentVO(
                    comment.getId(),
                    deleted ? DELETED_CONTENT : comment.getContent(),
                    deleted,
                    deleted ? null : authors.get(comment.getUserId()),
                    counts.get(comment.getId()).get(CounterMetric.COMMENT_REPLY),
                    comment.getCreatedAt());
        };
    }
}
