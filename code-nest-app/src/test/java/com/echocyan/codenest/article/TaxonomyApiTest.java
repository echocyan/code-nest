package com.echocyan.codenest.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class TaxonomyApiTest extends IntegrationTest {

    @Test
    void listsPresetCategoriesInSortOrder() {
        client.get().uri(API + "/categories")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.length()").value(Integer.class, n -> assertThat(n).isGreaterThan(0))
                .jsonPath("$.data[0].id").isNotEmpty()
                .jsonPath("$.data[0].name").isEqualTo("后端")
                .jsonPath("$.data[1].name").isEqualTo("前端");
    }

    @Test
    void listsPresetTags() {
        client.get().uri(API + "/tags")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data[?(@.name == 'Java')]").exists()
                .jsonPath("$.data[?(@.name == 'Redis')]").exists()
                .jsonPath("$.data[0].id").isNotEmpty();
    }
}
