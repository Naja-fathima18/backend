package com.app.backend.ratelimit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsToTenRequestsPerMinuteOnQuotes() {
        contextRunner.run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            assertThat(properties.limit()).isEqualTo(10);
            assertThat(properties.window()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.paths()).containsExactly("/api/quotes/**");
        });
    }

    @Test
    void bindsLimitWindowAndPathsFromProperties() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.limit=25", "rate-limit.window=90s", "rate-limit.paths=/api/a/**,/api/b/**")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.limit()).isEqualTo(25);
                    assertThat(properties.window()).isEqualTo(Duration.ofSeconds(90));
                    assertThat(properties.paths()).containsExactly("/api/a/**", "/api/b/**");
                });
    }

    @Test
    void failsStartupWhenLimitIsBelowOne() {
        contextRunner
                .withPropertyValues("rate-limit.limit=0")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("field 'limit'"));
    }

    @Test
    void failsStartupWhenWindowIsZero() {
        contextRunner
                .withPropertyValues("rate-limit.window=0s")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("window must be positive"));
    }

    @Test
    void failsStartupWhenWindowIsNegative() {
        contextRunner
                .withPropertyValues("rate-limit.window=-5s")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("window must be positive"));
    }

    @Test
    void failsStartupWhenNoPathIsProtected() {
        contextRunner
                .withPropertyValues("rate-limit.paths=")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("field 'paths'"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class PropertiesConfig {}
}
