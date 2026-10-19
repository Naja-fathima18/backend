package com.app.backend.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmationNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationNotifier.class);

    public void notifyConfirmed(BookingConfirmedEvent event) {
        log.info(
                "Booking {} confirmed with doctor {} starting at {}",
                event.bookingId(),
                event.doctorId(),
                event.startTime());
    }
}
