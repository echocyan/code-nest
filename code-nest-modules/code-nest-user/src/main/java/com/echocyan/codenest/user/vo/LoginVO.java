package com.echocyan.codenest.user.vo;

/**
 * 登录结果。客户端之后以 {@code Authorization: Bearer <token>} 携带 token。
 */
public record LoginVO(Long userId, String token) {
}
