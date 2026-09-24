package com.echocyan.codenest.article;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class CategoryApiTest extends IntegrationTest {

    @Test
    void listsPresetCategoriesInSortOrder() {
        client.get().uri(API + "/categories")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data[0].id").isNotEmpty()
                .jsonPath("$.data[0].name").isEqualTo("后端")
                .jsonPath("$.data[1].name").isEqualTo("前端");
    }
}
