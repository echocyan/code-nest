package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求。密码限定为可打印 ASCII 字符：BCrypt 最多只接受 72 字节，32 个 ASCII 字符不会超出。
 */
public record RegisterRequest(
        @NotNull @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "用户名需为 4–20 位字母、数字或下划线")
        String username,
        @NotNull @Pattern(regexp = "^[\\x20-\\x7E]{8,32}$", message = "密码需为 8–32 位英文字母、数字或符号")
        String password) {
}
