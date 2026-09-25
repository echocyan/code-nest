package com.echocyan.codenest.social.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.SocialErrorCode;
import com.echocyan.codenest.social.entity.Follow;
import com.echocyan.codenest.social.vo.FollowUserVO;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 关注与取关。两者都幂等，只有真的插入或删除了一行才更新被关注者的粉丝数和关注者的关注数；关注、取关成功时还会分别发出
 * {@code follow.created}、{@code follow.deleted}。
 */
public interface FollowService extends IService<Follow> {

    /**
     * @throws BizException {@link SocialErrorCode#CANNOT_FOLLOW_SELF} 对象是自己；
     *                      {@link SocialErrorCode#USER_NOT_FOUND} 对象不存在
     */
    void follow(long followerId, long authorId);

    /**
     * @throws BizException {@link SocialErrorCode#CANNOT_FOLLOW_SELF} 对象是自己；
     *                      {@link SocialErrorCode#USER_NOT_FOUND} 对象不存在
     */
    void unfollow(long followerId, long authorId);

    /**
     * 按关注时间倒序翻阅某个用户的粉丝，以关注记录的 ID 为游标。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     */
    CursorResult<FollowUserVO> listFollowers(long authorId, Long cursor, int size);

    /**
     * 按关注时间倒序翻阅某个用户关注的人，以关注记录的 ID 为游标。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     */
    CursorResult<FollowUserVO> listFollowings(long followerId, Long cursor, int size);

    /**
     * followerId 关注的全部用户，只读 (follower_id, author_id) 唯一索引。
     */
    List<Long> listAllFollowedAuthorIds(long followerId);

    /**
     * 按粉丝 ID 升序分页取出某个作者的粉丝，走 (author_id, follower_id) 索引，供 Feed 推送使用。
     *
     * @param afterFollowerId 只返回 ID 大于它的粉丝；为 null 时从头开始
     */
    List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit);

    /**
     * 给定用户中 followerId 关注了的那些，走 (follower_id, author_id) 唯一索引。
     */
    Set<Long> listFollowedAuthorIds(long followerId, Collection<Long> authorIds);
}
