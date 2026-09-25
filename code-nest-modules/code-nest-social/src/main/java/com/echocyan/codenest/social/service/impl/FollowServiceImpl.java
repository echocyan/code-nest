package com.echocyan.codenest.social.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.social.SocialErrorCode;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import com.echocyan.codenest.social.api.event.FollowDeletedEvent;
import com.echocyan.codenest.social.entity.Follow;
import com.echocyan.codenest.social.mapper.FollowMapper;
import com.echocyan.codenest.social.service.FollowService;
import com.echocyan.codenest.social.vo.FollowUserVO;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService,
        CounterSource {

    private final UserApi userApi;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public void follow(long followerId, long authorId) {
        requireOtherUser(followerId, authorId);
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setAuthorId(authorId);
        try {
            save(follow);
        } catch (DuplicateKeyException e) {
            // 已关注过（包括并发的重复请求），不产生变化；MySQL 只回滚这一条语句，事务可以继续
            return;
        }
        countFollow(followerId, authorId, 1);
        eventPublisher.publish(new FollowCreatedEvent(followerId, authorId));
    }

    @Override
    @Transactional
    public void unfollow(long followerId, long authorId) {
        requireOtherUser(followerId, authorId);
        boolean removed = lambdaUpdate()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getAuthorId, authorId)
                .remove();
        if (removed) {
            countFollow(followerId, authorId, -1);
            eventPublisher.publish(new FollowDeletedEvent(followerId, authorId));
        }
    }

    @Override
    public CursorResult<FollowUserVO> listFollowers(long authorId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Follow::getAuthorId, authorId)
                .lt(cursor != null, Follow::getId, cursor)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (size + 1))
                .list(), size, Follow::getFollowerId);
    }

    @Override
    public CursorResult<FollowUserVO> listFollowings(long followerId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .lt(cursor != null, Follow::getId, cursor)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (size + 1))
                .list(), size, Follow::getAuthorId);
    }

    @Override
    public List<Long> listAllFollowedAuthorIds(long followerId) {
        return lambdaQuery()
                .select(Follow::getAuthorId)
                .eq(Follow::getFollowerId, followerId)
                .list().stream()
                .map(Follow::getAuthorId)
                .toList();
    }

    @Override
    public List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit) {
        return lambdaQuery()
                .select(Follow::getFollowerId)
                .eq(Follow::getAuthorId, authorId)
                .gt(afterFollowerId != null, Follow::getFollowerId, afterFollowerId)
                .orderByAsc(Follow::getFollowerId)
                .last("LIMIT " + limit)
                .list().stream()
                .map(Follow::getFollowerId)
                .toList();
    }

    @Override
    public Set<Long> listFollowedAuthorIds(long followerId, Collection<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery()
                .select(Follow::getAuthorId)
                .eq(Follow::getFollowerId, followerId)
                .in(Follow::getAuthorId, authorIds)
                .list().stream()
                .map(Follow::getAuthorId)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.USER_FOLLOWER, CounterMetric.USER_FOLLOWING);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return switch (metric) {
            case USER_FOLLOWER -> baseMapper.countByAuthor(afterId, limit);
            case USER_FOLLOWING -> baseMapper.countByFollower(afterId, limit);
            default -> throw new IllegalArgumentException("不负责的计数指标: " + metric);
        };
    }

    /**
     * 多查了一条的结果转为游标分页：多出的那条只用来判断是否还有下一页。
     *
     * @param listedUserId 从关注记录中取出要列出的那一方的用户 ID
     */
    private CursorResult<FollowUserVO> toCursorResult(List<Follow> fetched, int size,
                                                      Function<Follow, Long> listedUserId) {
        boolean hasMore = fetched.size() > size;
        List<Follow> page = hasMore ? fetched.subList(0, size) : fetched;
        if (page.isEmpty()) {
            return CursorResult.empty();
        }
        Map<Long, UserBrief> users = userApi.getBriefs(page.stream().map(listedUserId).toList());
        List<FollowUserVO> list = page.stream()
                .map(follow -> new FollowUserVO(users.get(listedUserId.apply(follow)), follow.getCreatedAt()))
                .toList();
        return new CursorResult<>(list, hasMore ? page.getLast().getId() : null, hasMore);
    }

    private void requireOtherUser(long followerId, long authorId) {
        if (followerId == authorId) {
            throw new BizException(SocialErrorCode.CANNOT_FOLLOW_SELF);
        }
        if (!userApi.exists(authorId)) {
            throw new BizException(SocialErrorCode.USER_NOT_FOUND);
        }
    }

    private void countFollow(long followerId, long authorId, long delta) {
        counterApi.increment(CounterMetric.USER_FOLLOWER, authorId, delta);
        counterApi.increment(CounterMetric.USER_FOLLOWING, followerId, delta);
    }
}
