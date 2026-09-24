package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 新建或编辑文章的内容，编辑时整体替换。
 *
 * @param summary 不填或为空白时截取正文开头
 * @param tagIds  至多 5 个，重复的会合并
 */
public record ArticleRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 100_000) String content,
        @Size(max = 200) String summary,
        @Size(max = 512) @Pattern(regexp = "^https?://\\S+$", message = "封面需为 http(s) 地址") String coverUrl,
        @NotNull Long categoryId,
        @Size(max = 5) List<@NotNull Long> tagIds) {
}
