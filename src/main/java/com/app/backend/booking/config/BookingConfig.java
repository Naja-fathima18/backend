package com.app.backend.booking.config;

import java.time.Clock;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {

    public static final String CLOCK = "bookingClock";
    public static final String NOTIFICATION_EXECUTOR = "bookingNotificationExecutor";
    public static final String NOTIFICATION_THREAD_PREFIX = "booking-notify-";

    private static final Logger log = LoggerFactory.getLogger(BookingConfig.class);

    @Bean(name = CLOCK, defaultCandidate = false)
    public Clock bookingClock() {
        return Clock.systemDefaultZone();
    }

    @Bean(name = NOTIFICATION_EXECUTOR, defaultCandidate = false)
    public ThreadPoolTaskExecutor bookingNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(NOTIFICATION_THREAD_PREFIX);
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler(dropAndWarn());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    private static RejectedExecutionHandler dropAndWarn() {
        return (task, pool) -> log.warn("Booking notification dropped because the notification queue is full");
    }
}
