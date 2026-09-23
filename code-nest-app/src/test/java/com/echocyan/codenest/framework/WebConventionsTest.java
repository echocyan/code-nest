package com.echocyan.codenest.framework;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WebConventionsTest extends IntegrationTest {

    @Test
    void serializesLongAsStringTimeWithOffsetEnumAsNameAndKeepsNull() {
        client.get().uri(API + "/probe/sample")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.message").isEqualTo("ok")
                .jsonPath("$.data.id").isEqualTo("1234567890123456789")
                .jsonPath("$.data.at").isEqualTo("2026-09-23T10:00:00+08:00")
                .jsonPath("$.data.status").isEqualTo("PUBLISHED")
                .jsonPath("$.data").value(Map.class, data -> assertThat(data).containsEntry("nothing", null));
    }

    @Test
    void keepsPrimitiveLongAsNumberInPageResult() {
        client.get().uri(API + "/probe/page")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"list\":[\"1234567890123456789\"]")
                        .contains("\"total\":42")
                        .contains("\"page\":1")
                        .contains("\"size\":20"));
    }

    @Test
    void acceptsOffsetTimeInRequestBody() {
        client.post().uri(API + "/probe/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"n\",\"at\":\"2026-09-23T02:00:00Z\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.at").isEqualTo("2026-09-23T10:00:00+08:00");
    }

    @Test
    void bodyValidationFailureIs400() {
        client.post().uri(API + "/probe/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(90400)
                .jsonPath("$.data").isEmpty();
    }

    @Test
    void parameterValidationAndTypeMismatchAre400() {
        client.get().uri(API + "/probe/size?size=51")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
        client.get().uri(API + "/probe/size?size=abc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }

    @Test
    void springMvcRequestErrorsKeepTheirStatusWithCommonCode() {
        client.get().uri(API + "/probe/header")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
        client.post().uri(API + "/probe/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .body("name")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo(90400);
        client.delete().uri(API + "/probe/sample")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }

    @Test
    void bizExceptionUsesItsHttpStatus() {
        client.get().uri(API + "/probe/forbidden")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(90403);
    }

    @Test
    void unexpectedExceptionIs500WithoutLeakingDetails() {
        client.get().uri(API + "/probe/boom")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo(99999)
                .jsonPath("$.message").value(String.class, m -> assertThat(m).doesNotContain("boom"));
    }

    @Test
    void unknownPathIs404() {
        client.get().uri(API + "/no-such-thing")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(90404);
    }

    @Test
    void controllersLiveUnderApiPrefix() {
        client.get().uri("/probe/sample")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void openApiDocumentListsBusinessEndpoints() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/categories']").exists();
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
