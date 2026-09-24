package com.echocyan.codenest.article;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class TagApiTest extends IntegrationTest {

    @Test
    void listsPresetTags() {
        client.get().uri(API + "/tags")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data[0].id").isNotEmpty()
                .jsonPath("$.data[?(@.name == 'Java')]").exists()
                .jsonPath("$.data[?(@.name == 'Redis')]").exists();
    }
}
