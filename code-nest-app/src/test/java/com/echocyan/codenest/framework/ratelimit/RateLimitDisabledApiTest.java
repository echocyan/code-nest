package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 测试默认关闭限流（见 {@link IntegrationTest}），限额沿用默认配置：登录每 IP 每分钟 10 次。
 */
class RateLimitDisabledApiTest extends IntegrationTest {

    @Test
    void requestsBeyondTheLimitPassWhenDisabled() {
        String username = uniqueUsername();
        register(username);

        for (int i = 0; i < 11; i++) {
            login(username);
        }
    }
}
