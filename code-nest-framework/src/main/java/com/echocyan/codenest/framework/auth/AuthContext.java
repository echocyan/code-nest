package com.echocyan.codenest.framework.auth;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前请求的登录态。只在 Controller 中使用，Service 通过参数接收用户 ID，不接触 Sa-Token。
 */
public final class AuthContext {

    private AuthContext() {
    }

    /**
     * 以该用户身份登录当前设备。
     *
     * @param userId 登录用户的 ID
     * @return 新签发的 token，客户端以 {@code Authorization: Bearer <token>} 携带
     */
    public static String login(long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    /**
     * 退出当前设备，同一账号在其他设备上的 token 不受影响。
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 当前登录用户的 ID。
     *
     * @throws cn.dev33.satoken.exception.NotLoginException 未登录
     */
    public static long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
