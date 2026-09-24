package com.echocyan.codenest.user.vo;

import java.time.LocalDateTime;

public record UserProfileVO(Long id, String username, String nickname, String avatarUrl, String bio,
                            LocalDateTime createdAt) {
}
