package com.app.backend.booking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking.expiry-sweep-enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryJob {

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(
            initialDelayString = "${booking.expiry-sweep-interval:30s}",
            fixedDelayString = "${booking.expiry-sweep-interval:30s}")
    public void expireOverdueHolds() {
        bookingService.expireOverdueHolds();
    }
}
