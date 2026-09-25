package com.echocyan.codenest.social.vo;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 粉丝列表或关注列表中的一项。
 */
public record FollowUserVO(UserBrief user, LocalDateTime followedAt) {
}
