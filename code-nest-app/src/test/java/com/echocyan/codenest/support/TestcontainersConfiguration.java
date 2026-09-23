package com.echocyan.codenest.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试用的中间件容器。所有测试共用同一个 Spring 上下文缓存，因此每种容器只启动一次。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String ES_IMAGE = "code-nest/elasticsearch-ik:8.19.21";

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("code_nest")
                .withEnv("TZ", "Asia/Shanghai");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:8.6").withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:4.3.5-management");
    }

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {
        return new ElasticsearchContainer(elasticsearchImage())
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }

    /**
     * 复用仓库里带 IK 插件的 Dockerfile；镜像不随测试结束删除，第二次起直接命中缓存。
     */
    private static DockerImageName elasticsearchImage() {
        Path dockerfileDir = findRepoPath("docker/elasticsearch");
        String image = new ImageFromDockerfile(ES_IMAGE, false)
                .withFileFromPath(".", dockerfileDir)
                .get();
        return DockerImageName.parse(image)
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");
    }

    private static Path findRepoPath(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relative + " from working directory");
    }
}
