package com.echocyan.codenest;

import com.echocyan.codenest.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * 不依赖 docker compose，直接用 Testcontainers 拉起中间件运行应用。
 */
public class TestCodeNestApplication {

    public static void main(String[] args) {
        SpringApplication.from(CodeNestApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
