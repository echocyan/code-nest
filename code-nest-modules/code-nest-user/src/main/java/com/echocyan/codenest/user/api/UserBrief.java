package com.echocyan.codenest.user.api;

/**
 * 在文章、评论、通知等处展示的用户简要信息。
 */
public record UserBrief(Long id, String nickname, String avatarUrl) {
}
