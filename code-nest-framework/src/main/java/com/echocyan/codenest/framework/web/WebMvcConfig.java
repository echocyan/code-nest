package com.echocyan.codenest.framework.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    private static final String BASE_PACKAGE = "com.echocyan.codenest";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 只给本项目的 Controller 加前缀，SpringDoc、Actuator 等第三方端点不受影响
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage(BASE_PACKAGE)
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
