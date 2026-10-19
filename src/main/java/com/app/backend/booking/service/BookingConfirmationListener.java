package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingConfirmationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationListener.class);

    private final BookingConfirmationNotifier notifier;

    public BookingConfirmationListener(BookingConfirmationNotifier notifier) {
        this.notifier = notifier;
    }

    @Async(BookingAsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            notifier.notifyConfirmed(event);
        } catch (RuntimeException ex) {
            log.error("Failed to send confirmation notification for booking {}", event.bookingId(), ex);
        }
    }
}
