package com.app.backend.ratelimit.config;

import com.app.backend.ratelimit.api.RateLimitFilter;
import com.app.backend.ratelimit.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    public static final String CLOCK_BEAN = "rateLimitClock";

    @Bean(CLOCK_BEAN)
    Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, properties, objectMapper));
        registration.setName("rateLimitFilter");
        return registration;
    }
}
