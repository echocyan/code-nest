package com.echocyan.codenest.social.service;

import com.echocyan.codenest.counter.api.IdCount;

import java.util.List;

/**
 * {@link FeedStore} 需要的关注关系查询，直接读关注表。
 */
public interface FollowerGraph {

    /**
     * followerId 关注的全部用户，只读 (follower_id, author_id) 唯一索引。
     */
    List<Long> listAllFollowedAuthorIds(long followerId);

    /**
     * 按粉丝 ID 升序分页取出某个作者的粉丝，走 (author_id, follower_id) 索引。
     *
     * @param afterFollowerId 只返回 ID 大于它的粉丝；为 null 时从头开始
     */
    List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit);

    /**
     * 作者当前的粉丝数，直接统计关注表，走 (author_id, follower_id) 索引。
     */
    long countFollowers(long authorId);

    /**
     * 用户 ID 大于 afterAuthorId、至少有一个粉丝的各用户的粉丝数，按用户 ID 升序。
     */
    List<IdCount> countFollowersAfter(long afterAuthorId, int limit);
}
