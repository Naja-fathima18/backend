package com.app.backend.ratelimit.config;

import com.app.backend.ratelimit.api.RateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimitInterceptor> interceptor;
    private final ObjectProvider<RateLimitProperties> properties;

    public RateLimitWebConfig(
            ObjectProvider<RateLimitInterceptor> interceptor, ObjectProvider<RateLimitProperties> properties) {
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimitInterceptor rateLimitInterceptor = interceptor.getIfAvailable();
        RateLimitProperties rateLimitProperties = properties.getIfAvailable();
        if (rateLimitInterceptor == null || rateLimitProperties == null) {
            return;
        }
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(rateLimitProperties.paths());
    }
}
