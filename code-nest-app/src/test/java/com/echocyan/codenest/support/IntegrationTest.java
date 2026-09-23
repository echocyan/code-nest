package com.echocyan.codenest.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * HTTP 集成测试基类：真实端口 + 真实中间件容器。子类通过 {@link #client} 调用接口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String API = "/api/v1";

    @LocalServerPort
    private int port;

    protected RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
