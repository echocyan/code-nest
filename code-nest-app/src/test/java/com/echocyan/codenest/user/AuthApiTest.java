package com.echocyan.codenest.user;

import com.echocyan.codenest.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthApiTest extends IntegrationTest {

    @Test
    void registeredUserIsLoggedInWithNicknameDefaultingToUsername() {
        String username = uniqueUsername();

        String token = register(username);

        withToken(token).get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isNotEmpty()
                .jsonPath("$.data.username").isEqualTo(username)
                .jsonPath("$.data.nickname").isEqualTo(username);
    }

    @Test
    void protectedEndpointRequiresLogin() {
        client.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(90401);
    }

    @Test
    void tokenWithoutBearerPrefixIsRejected() {
        String token = register(uniqueUsername());

        client.get().uri(API + "/users/me")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(90401);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() {
        String username = uniqueUsername();
        register(username);

        for (String name : new String[]{username, uniqueUsername()}) {
            client.post().uri(API + "/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", name, "password", "wrong-password"))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo(10002);
        }
    }

    @Test
    void logoutOnlyInvalidatesTheCurrentDevice() {
        String username = uniqueUsername();
        String phone = register(username);
        String laptop = login(username);

        withToken(phone).post().uri(API + "/auth/logout")
                .exchange()
                .expectStatus().isOk();

        withToken(phone).get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
        withToken(laptop).get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.username").isEqualTo(username);
    }

    @Test
    void logoutRequiresLogin() {
        client.post().uri(API + "/auth/logout")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(90401);
    }

    @Test
    void usernameCanOnlyBeRegisteredOnce() {
        String username = uniqueUsername();
        register(username);

        client.post().uri(API + "/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo(10001);
    }

    @ParameterizedTest
    @CsvSource({
            "abc, Passw0rd!",                   // 用户名少于 4 位
            "abcdefghijklmnopqrstu, Passw0rd!", // 用户名多于 20 位
            "has space, Passw0rd!",             // 用户名含非法字符
            "中文用户名, Passw0rd!",
            "valid_name, 1234567",              // 密码少于 8 位
            "valid_name, 123456789012345678901234567890123", // 密码多于 32 位
    })
    void registrationRejectsInvalidUsernameOrPassword(String username, String password) {
        client.post().uri(API + "/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }
}
