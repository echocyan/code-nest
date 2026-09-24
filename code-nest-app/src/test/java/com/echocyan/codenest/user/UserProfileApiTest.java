package com.echocyan.codenest.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

class UserProfileApiTest extends IntegrationTest {

    @Test
    void userCanEditNicknameAvatarAndBioButNotUsername() {
        String username = uniqueUsername();
        RestTestClient me = withToken(register(username));

        me.put().uri(API + "/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", "someone_else",
                        "nickname", "码农小王",
                        "avatarUrl", "https://example.com/a.png",
                        "bio", "写 Java 的"))
                .exchange()
                .expectStatus().isOk();

        me.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.username").isEqualTo(username)
                .jsonPath("$.data.nickname").isEqualTo("码农小王")
                .jsonPath("$.data.avatarUrl").isEqualTo("https://example.com/a.png")
                .jsonPath("$.data.bio").isEqualTo("写 Java 的");
    }

    @Test
    void omittedAvatarAndBioAreCleared() {
        RestTestClient me = withToken(register(uniqueUsername()));
        me.put().uri(API + "/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nickname", "n1", "avatarUrl", "https://example.com/a.png", "bio", "b"))
                .exchange()
                .expectStatus().isOk();

        me.put().uri(API + "/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nickname", "n2"))
                .exchange()
                .expectStatus().isOk();

        me.get().uri(API + "/users/me")
                .exchange()
                .expectBody()
                .jsonPath("$.data.nickname").isEqualTo("n2")
                .jsonPath("$.data").value(Map.class, data -> assertThat(data)
                        .containsEntry("avatarUrl", null)
                        .containsEntry("bio", null));
    }

    @Test
    void profileEditIsValidated() {
        RestTestClient me = withToken(register(uniqueUsername()));
        Map<String, Object> blankNickname = new HashMap<>(Map.of("nickname", " "));
        Map<String, Object> longNickname = new HashMap<>(Map.of("nickname", "一二三四五六七八九十一二三四五六七八九十一"));
        Map<String, Object> longBio = new HashMap<>(Map.of("nickname", "ok", "bio", "x".repeat(201)));
        Map<String, Object> scriptAvatar = new HashMap<>(Map.of("nickname", "ok", "avatarUrl", "javascript:alert(1)"));

        for (Map<String, Object> body : List.of(blankNickname, longNickname, longBio, scriptAvatar)) {
            me.put().uri(API + "/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
    }

    @Test
    void anyoneCanViewAUsersHomepage() {
        String username = uniqueUsername();
        String id = idOf(withToken(register(username)));

        client.get().uri(API + "/users/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(id)
                .jsonPath("$.data.username").isEqualTo(username)
                .jsonPath("$.data.nickname").isEqualTo(username);
    }

    @Test
    void homepageOfUnknownUserIs404() {
        client.get().uri(API + "/users/{id}", "1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(10003);
    }

    private String idOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }
}
