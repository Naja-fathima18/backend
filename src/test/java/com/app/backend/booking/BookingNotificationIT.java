package com.app.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.app.backend.booking.config.BookingAsyncConfig;
import com.app.backend.booking.service.BookingConfirmedEvent;
import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.HoldExpiredException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

class BookingNotificationIT extends BookingIntegrationTestSupport {

    private static final Duration QUIET_PERIOD = Duration.ofMillis(500);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void notifiesAsynchronouslyAfterConfirmCommits() {
        AtomicReference<String> notifyingThread = new AtomicReference<>();
        doAnswer(invocation -> {
                    notifyingThread.set(Thread.currentThread().getName());
                    return null;
                })
                .when(notifier)
                .notifyConfirmed(any());
        Long bookingId =
                bookingService.hold(DOCTOR_ID, Instant.parse(TEN_AM), "alice").id();

        ResponseEntity<Map> response = confirm(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BookingConfirmedEvent expected = new BookingConfirmedEvent(bookingId, DOCTOR_ID, Instant.parse(TEN_AM));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(notifier).notifyConfirmed(expected));
        assertThat(notifyingThread.get()).startsWith(BookingAsyncConfig.NOTIFICATION_THREAD_PREFIX);
    }

    @Test
    void doesNotNotifyWhenConfirmTransactionRollsBack() {
        Long bookingId =
                bookingService.hold(DOCTOR_ID, Instant.parse(TEN_AM), "alice").id();

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.confirm(bookingId, "alice");
            status.setRollbackOnly();
        });

        await().during(QUIET_PERIOD)
                .atMost(QUIET_PERIOD.multipliedBy(4))
                .untilAsserted(() -> verify(notifier, never()).notifyConfirmed(any()));
        assertThat(bookingService.get(bookingId).status().name()).isEqualTo("HELD");
    }

    @Test
    void doesNotNotifyWhenConfirmFailsBecauseHoldExpired() {
        Long bookingId =
                bookingService.hold(DOCTOR_ID, Instant.parse(TEN_AM), "alice").id();
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> bookingService.confirm(bookingId, "alice")).isInstanceOf(HoldExpiredException.class);

        await().during(QUIET_PERIOD)
                .atMost(QUIET_PERIOD.multipliedBy(4))
                .untilAsserted(() -> verify(notifier, never()).notifyConfirmed(any()));
    }

    @Test
    void notifierFailureDoesNotAffectTheConfirmedBooking() {
        doAnswer(invocation -> {
                    throw new IllegalStateException("mail server down");
                })
                .when(notifier)
                .notifyConfirmed(any());
        Long bookingId =
                bookingService.hold(DOCTOR_ID, Instant.parse(TEN_AM), "alice").id();

        ResponseEntity<Map> response = confirm(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(notifier).notifyConfirmed(any()));
        assertThat(getBooking(bookingId).getBody().get("status")).isEqualTo("CONFIRMED");
    }
}
