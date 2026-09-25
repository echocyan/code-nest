package com.echocyan.codenest.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 集成测试用的中间件容器，声明为静态字段：即使测试用到了多个 Spring 上下文（如不同的模式开关），
 * 同一个 JVM 里也只有一组容器。
 * <p>
 * Spring 关闭上下文时会停掉它登记的容器，而多个上下文共用这组容器，先关闭的上下文会让其余上下文在关闭时
 * 连不上中间件、拖住 JVM 退出。所以这些容器的 {@code stop()} 什么都不做，JVM 退出后由 Testcontainers 的 Ryuk 清理。
 */
public final class SharedContainers {

    @ServiceConnection
    public static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.3.5-management") {
        @Override
        public void stop() {
        }
    };
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4") {
        @Override
        public void stop() {
        }
    }
            .withDatabaseName("code_nest")
            .withEnv("TZ", "Asia/Shanghai");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new RedisContainer().withExposedPorts(6379);
    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(elasticsearchImage()) {
        @Override
        public void stop() {
        }
    }
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    private static final String ES_IMAGE = "code-nest/elasticsearch-ik:9.4.5";

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

    /**
     * {@code GenericContainer<?>} 无法用匿名子类覆盖 {@code stop()}，单独声明。
     */
    private static final class RedisContainer extends GenericContainer<RedisContainer> {

        RedisContainer() {
            super("redis:8.6");
        }

        @Override
        public void stop() {
        }
    }
}
