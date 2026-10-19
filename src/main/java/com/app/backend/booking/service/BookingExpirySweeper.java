package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "booking.expiry-sweep", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpirySweeper implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirySweeper.class);

    private final BookingExpiryService expiryService;
    private final Duration interval;

    public BookingExpirySweeper(BookingExpiryService expiryService, BookingProperties properties) {
        this.expiryService = expiryService;
        this.interval = properties.expirySweep().interval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::sweep, interval);
    }

    void sweep() {
        int expired = expiryService.expireStaleHolds();
        if (expired > 0) {
            log.info("Expired {} stale booking holds", expired);
        }
    }
}
