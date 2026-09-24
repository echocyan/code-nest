package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论。
 */
public record CommentRequest(@NotBlank @Size(max = 1000) String content) {
}
