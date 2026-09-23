package com.echocyan.codenest.framework.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.net.URI;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4 的 ES 自动配置基于 9.x 的 Rest5Client，与 8.19 服务端不兼容，这里用 8.x 客户端自行装配。
 * 连接信息优先取 Testcontainers / Docker Compose 提供的 {@link ElasticsearchConnectionDetails}，
 * 否则读 {@code spring.elasticsearch.uris/username/password}。
 */
@Configuration(proxyBeanMethods = false)
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(ObjectProvider<ElasticsearchConnectionDetails> connectionDetails,
                                              @Value("${spring.elasticsearch.uris:http://localhost:9200}") List<URI> uris,
                                              @Value("${spring.elasticsearch.username:#{null}}") String username,
                                              @Value("${spring.elasticsearch.password:#{null}}") String password) {
        ElasticsearchConnectionDetails details = connectionDetails.getIfAvailable();
        if (details != null) {
            username = details.getUsername();
            password = details.getPassword();
        }
        List<URI> nodes = details == null ? uris
                : details.getNodes().stream()
                        .map(node -> URI.create(node.protocol().name().toLowerCase() + "://" + node.hostname() + ":" + node.port()))
                        .toList();
        RestClientBuilder builder = RestClient.builder(nodes.stream()
                .map(uri -> new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                .toArray(HttpHost[]::new));
        if (username != null) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(http -> http.setDefaultCredentialsProvider(credentials));
        }
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        // 使用独立的 Mapper，避免 Web 层的 JSON 约定（如 Long 转字符串）影响索引文档
        return new ElasticsearchClient(new RestClientTransport(restClient, new Jackson3JsonpMapper()));
    }
}
