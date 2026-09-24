package com.echocyan.codenest.framework.openapi;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI codeNestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("码巢 code-nest API")
                        .description("开发者技术社区后端接口")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("登录或注册返回的 token。请求头格式为 `Authorization: Bearer <token>`，"
                                + "缺少 `Bearer ` 前缀会被视为未登录。")));
    }

    /**
     * 与 SaInterceptor 的规则保持一致：方法和类上都没有 {@link SaIgnore} 的接口才标注为需要登录。
     */
    @Bean
    public OperationCustomizer loginRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            boolean ignored = handlerMethod.hasMethodAnnotation(SaIgnore.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(SaIgnore.class);
            return ignored ? operation : operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
        };
    }
}
