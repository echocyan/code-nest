package com.echocyan.codenest.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * HTTP 集成测试基类：真实端口 + 真实中间件容器。子类通过 {@link #client} 匿名调用接口，
 * 通过 {@link #withToken} 以登录用户身份调用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, SaTokenDaoRebinding.class})
public abstract class IntegrationTest {

    protected static final String API = "/api/v1";

    protected static final String PASSWORD = "Passw0rd!";

    @LocalServerPort
    private int port;

    protected RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /**
     * 所有测试共用一个数据库，用户名需要全局唯一。
     */
    protected static String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    }

    /**
     * 用 {@link #PASSWORD} 注册一个用户，返回注册后自动登录得到的 token。
     */
    protected String register(String username) {
        return authenticate("/auth/register", username);
    }

    /**
     * 用 {@link #PASSWORD} 登录，返回本次登录签发的 token（相当于一台新设备）。
     */
    protected String login(String username) {
        return authenticate("/auth/login", username);
    }

    /**
     * 返回之后的请求都带上 {@code Authorization: Bearer <token>} 的客户端。
     */
    protected RestTestClient withToken(String token) {
        return client.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    private String authenticate(String path, String username) {
        AtomicReference<String> token = new AtomicReference<>();
        client.post().uri(API + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.token").value(String.class, token::set);
        return token.get();
    }
}
