package com.echocyan.codenest.framework;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ElasticsearchSetupTest extends IntegrationTest {

    @Autowired
    ElasticsearchClient elasticsearchClient;

    @Test
    void ikAnalyzerIsInstalled() throws Exception {
        AnalyzeResponse response = elasticsearchClient.indices()
                .analyze(a -> a.analyzer("ik_smart").text("开发者技术社区"));

        assertThat(response.tokens()).extracting(AnalyzeToken::token)
                .contains("开发者", "技术", "社区");
    }
}
