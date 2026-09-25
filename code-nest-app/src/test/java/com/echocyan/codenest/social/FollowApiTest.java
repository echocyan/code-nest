package com.echocyan.codenest.social;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

class FollowApiTest extends IntegrationTest {

    @Test
    void followIsIdempotentAndCountsForBothSides() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient author = withToken(register(uniqueUsername()));
        String readerId = idOf(reader);
        String authorId = idOf(author);

        follow(reader, authorId).expectStatus().isOk();
        follow(reader, authorId).expectStatus().isOk();
        expectCounts(authorId, 1, 0);
        expectCounts(readerId, 0, 1);

        unfollow(reader, authorId).expectStatus().isOk();
        unfollow(reader, authorId).expectStatus().isOk();
        expectCounts(authorId, 0, 0);
        expectCounts(readerId, 0, 0);
    }

    @Test
    void cannotFollowYourselfOrAnUnknownUser() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        String readerId = idOf(reader);

        for (RestTestClient.ResponseSpec response : List.of(follow(reader, readerId), unfollow(reader, readerId))) {
            response.expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(40001);
        }
        for (RestTestClient.ResponseSpec response : List.of(follow(reader, "1"), unfollow(reader, "1"))) {
            response.expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo(40002);
        }
        expectCounts(readerId, 0, 0);
    }

    @Test
    void followersArePagedNewestFollowFirst() {
        String authorId = idOf(withToken(register(uniqueUsername())));
        String firstName = uniqueUsername();
        RestTestClient first = withToken(register(firstName));
        RestTestClient second = withToken(register(uniqueUsername()));
        RestTestClient third = withToken(register(uniqueUsername()));
        for (RestTestClient follower : List.of(second, first, third)) {
            follow(follower, authorId).expectStatus().isOk();
        }
        // 关注别人、关注后又取关的，都不在粉丝列表里
        follow(first, idOf(third)).expectStatus().isOk();
        RestTestClient gone = withToken(register(uniqueUsername()));
        follow(gone, authorId).expectStatus().isOk();
        unfollow(gone, authorId).expectStatus().isOk();

        AtomicReference<String> cursor = new AtomicReference<>();
        client.get().uri(API + "/users/{id}/followers?size=2", authorId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].user.id").isEqualTo(List.of(idOf(third), idOf(first)))
                .jsonPath("$.data.list[1].user.nickname").isEqualTo(firstName)
                .jsonPath("$.data.list[0].followedAt").isNotEmpty()
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);
        client.get().uri(API + "/users/{id}/followers?size=2&cursor={cursor}", authorId, cursor.get())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].user.id").isEqualTo(List.of(idOf(second)))
                .jsonPath("$.data.hasMore").isEqualTo(false)
                .jsonPath("$.data.nextCursor").isEmpty();
    }

    @Test
    void followingsArePagedNewestFollowFirst() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        String firstName = uniqueUsername();
        String first = idOf(withToken(register(firstName)));
        String second = idOf(withToken(register(uniqueUsername())));
        String third = idOf(withToken(register(uniqueUsername())));
        String gone = idOf(withToken(register(uniqueUsername())));
        for (String authorId : List.of(second, first, gone, third)) {
            follow(reader, authorId).expectStatus().isOk();
        }
        unfollow(reader, gone).expectStatus().isOk();
        String readerId = idOf(reader);

        AtomicReference<String> cursor = new AtomicReference<>();
        client.get().uri(API + "/users/{id}/followings?size=2", readerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].user.id").isEqualTo(List.of(third, first))
                .jsonPath("$.data.list[1].user.nickname").isEqualTo(firstName)
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);
        client.get().uri(API + "/users/{id}/followings?size=2&cursor={cursor}", readerId, cursor.get())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].user.id").isEqualTo(List.of(second))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void followStatesTellWhomIFollow() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient followedAuthor = withToken(register(uniqueUsername()));
        String followed = idOf(followedAuthor);
        String other = idOf(withToken(register(uniqueUsername())));
        follow(reader, followed).expectStatus().isOk();
        // 别人的关注不影响我的状态
        follow(followedAuthor, other).expectStatus().isOk();

        reader.get().uri(API + "/users/follow-states?ids={ids}", String.join(",", followed, other))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data['%s']".formatted(followed)).isEqualTo(true)
                .jsonPath("$.data['%s']".formatted(other)).isEqualTo(false);
    }

    @Test
    void followStatesAcceptAtMostFiftyIds() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        String fiftyOne = String.join(",", IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).toList());

        reader.get().uri(API + "/users/follow-states?ids={ids}", fiftyOne)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }

    private RestTestClient.ResponseSpec follow(RestTestClient follower, String userId) {
        return follower.put().uri(API + "/users/{id}/follow", userId).exchange();
    }

    private RestTestClient.ResponseSpec unfollow(RestTestClient follower, String userId) {
        return follower.delete().uri(API + "/users/{id}/follow", userId).exchange();
    }

    private void expectCounts(String userId, int followers, int followings) {
        eventually(() -> client.get().uri(API + "/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.counts.followerCount").isEqualTo(followers)
                .jsonPath("$.data.counts.followingCount").isEqualTo(followings));
    }

    private String idOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }
}
