package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 没有配置可信代理：所有请求都按本机 IP 限流。本上下文只有这一个测试会用本机 IP 的额度。
 */
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.limits.search-per-minute=3",
})
class RateLimitUntrustedProxyApiTest extends IntegrationTest {

    @Test
    void forgedForwardedForIsIgnored() {
        for (int i = 0; i < 3; i++) {
            search("10.0.0." + i).expectStatus().isOk();
        }

        search("10.0.1.1").expectStatus().isEqualTo(429);
    }

    private RestTestClient.ResponseSpec search(String forgedIp) {
        return client.get().uri(API + "/search/articles?q=redis")
                .header("X-Forwarded-For", forgedIp)
                .exchange();
    }
}
