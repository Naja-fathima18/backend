package com.app.backend.booking.service;

import com.app.backend.booking.domain.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationSender.class);

    public void sendConfirmation(BookingConfirmedEvent event) {
        log.info(
                "Booking {} confirmed for doctor {} at {} {}",
                event.bookingId(),
                event.doctorId(),
                event.date(),
                event.startTime());
    }
}
