package com.echocyan.codenest.framework.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI codeNestOpenApi() {
        return new OpenAPI().info(new Info()
                .title("码巢 code-nest API")
                .description("开发者技术社区后端接口")
                .version("v1"));
    }
}
