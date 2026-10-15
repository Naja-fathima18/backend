package com.app.backend.booking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BookingConfigTest {

    private static final Clock OTHER_FEATURE_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Configuration
    static class OtherFeatureClock {

        @Bean
        Clock otherFeatureClock() {
            return OTHER_FEATURE_CLOCK;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(BookingConfig.class);

    @Test
    void bookingClockDoesNotCompeteWithAnotherFeaturesClock() {
        runner.withUserConfiguration(OtherFeatureClock.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Clock.class)).isSameAs(OTHER_FEATURE_CLOCK);
            assertThat(context.getBean(BookingConfig.CLOCK, Clock.class)).isNotSameAs(OTHER_FEATURE_CLOCK);
        });
    }

    @Test
    void notificationExecutorKeepsTheApplicationTaskExecutor() {
        runner.run(context -> {
            assertThat(context).hasBean("applicationTaskExecutor");
            assertThat(context.getBean(BookingConfig.NOTIFICATION_EXECUTOR, ThreadPoolTaskExecutor.class)
                            .getThreadNamePrefix())
                    .isEqualTo(BookingConfig.NOTIFICATION_THREAD_PREFIX);
            assertThat(context.getBean(Executor.class)).isSameAs(context.getBean("applicationTaskExecutor"));
        });
    }
}
