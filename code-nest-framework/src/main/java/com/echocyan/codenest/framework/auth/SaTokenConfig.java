package com.echocyan.codenest.framework.auth;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.echocyan.codenest.framework.web.WebMvcConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 业务接口默认要求登录，公开接口在 Controller 类或方法上标注 {@code @SaIgnore}。
 */
@Configuration(proxyBeanMethods = false)
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只检查 Controller 方法：不存在的路径由后续处理返回 404，而不是 401
        registry.addInterceptor(new SaInterceptor(handler -> {
                    if (handler instanceof HandlerMethod) {
                        StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns(WebMvcConfig.API_PREFIX + "/**");
    }
}
