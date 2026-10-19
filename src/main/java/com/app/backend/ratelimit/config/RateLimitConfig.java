package com.app.backend.ratelimit.config;

import com.app.backend.ratelimit.api.RateLimitInterceptor;
import com.app.backend.ratelimit.service.FixedWindowRateLimiter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    FixedWindowRateLimiter fixedWindowRateLimiter(RateLimitProperties properties, ObjectProvider<Clock> clock) {
        return new FixedWindowRateLimiter(properties.limit(), properties.window(), clock.getIfUnique(Clock::systemUTC));
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(FixedWindowRateLimiter rateLimiter) {
        return new RateLimitInterceptor(rateLimiter);
    }

    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitInterceptor interceptor, RateLimitProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns(properties.paths());
            }
        };
    }
}
