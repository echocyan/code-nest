package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "用户名需为 4–20 位字母、数字或下划线")
        String username,
        @NotNull @Size(min = 8, max = 32, message = "密码长度需为 8–32 位")
        String password) {
}
