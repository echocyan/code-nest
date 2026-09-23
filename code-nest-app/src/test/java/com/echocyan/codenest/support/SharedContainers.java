package com.echocyan.codenest.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试用的中间件容器，声明为静态字段：即使测试用到了多个 Spring 上下文（如不同的模式开关），
 * 同一个 JVM 里也只有一组容器。
 */
final class SharedContainers {

    private static final String ES_IMAGE = "code-nest/elasticsearch-ik:9.4.5";

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("code_nest")
            .withEnv("TZ", "Asia/Shanghai");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6").withExposedPorts(6379);

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.3.5-management");

    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(elasticsearchImage())
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    private SharedContainers() {
    }

    /**
     * 复用仓库里带 IK 插件的 Dockerfile；镜像不随测试结束删除，第二次起直接命中缓存。
     */
    private static DockerImageName elasticsearchImage() {
        String image = new ImageFromDockerfile(ES_IMAGE, false)
                .withFileFromPath(".", findRepoPath("docker/elasticsearch"))
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
