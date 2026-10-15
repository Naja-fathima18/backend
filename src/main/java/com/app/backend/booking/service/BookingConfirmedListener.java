package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingConfig;
import com.app.backend.booking.domain.BookingConfirmedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingConfirmedListener {

    private final BookingNotificationSender notificationSender;

    public BookingConfirmedListener(BookingNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @Async(BookingConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationSender.sendConfirmation(event);
    }
}
