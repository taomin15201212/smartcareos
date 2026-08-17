package com.smartcareos.identity;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final ApiKeyAuthenticationInterceptor interceptor;
    private final RoleAuthorizationInterceptor authorization;
    public WebConfiguration(ApiKeyAuthenticationInterceptor interceptor,
            RoleAuthorizationInterceptor authorization) {
        this.interceptor = interceptor; this.authorization = authorization;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/system/health");
        registry.addInterceptor(authorization).addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/system/health");
    }
}
