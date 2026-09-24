package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改资料是整体替换：未提供的头像、简介会被清空。用户名不可修改，请求中的 username 字段会被忽略。
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 20) String nickname,
        @Size(max = 512) String avatarUrl,
        @Size(max = 200) String bio) {
}
