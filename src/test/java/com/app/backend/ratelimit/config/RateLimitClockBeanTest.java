package com.app.backend.ratelimit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RateLimitClockBeanTest {

    private static final Clock OTHER_FEATURE_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfig.class, RateLimiter.class, UnqualifiedClockConsumer.class)
            .withBean(ObjectMapper.class);

    @Test
    void isNotOfferedForUnqualifiedClockInjection() {
        contextRunner.run(context -> assertThat(context)
                .getFailure()
                .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                .rootCause()
                .hasMessageContaining(Clock.class.getName()));
    }

    @Test
    void leavesUnqualifiedClockInjectionToAnotherFeaturesClock() {
        contextRunner
                .withBean("otherFeatureClock", Clock.class, () -> OTHER_FEATURE_CLOCK)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UnqualifiedClockConsumer.class).clock())
                            .isSameAs(OTHER_FEATURE_CLOCK);
                    assertThat(context.getBean(RateLimitConfig.CLOCK_BEAN)).isNotSameAs(OTHER_FEATURE_CLOCK);
                });
    }

    record UnqualifiedClockConsumer(Clock clock) {}
}
