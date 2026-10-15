package com.app.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.app.backend.booking.config.BookingConfig;
import com.app.backend.booking.domain.BookingConfirmedEvent;
import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.domain.HoldExpiredException;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.booking.service.BookingNotificationSender;
import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.BookingView;
import com.app.backend.booking.service.HoldRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "booking.expiry-sweep-enabled=false")
class BookingNotificationIT {

    private static final LocalDate DATE = LocalDate.of(2032, 6, 1);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @TestBean(name = BookingConfig.CLOCK, methodName = "com.app.backend.booking.BookingTestClock#bookingClock")
    private Clock bookingClock;

    private final MutableClock clock = BookingTestClock.CLOCK;

    @MockitoBean
    private BookingNotificationSender notificationSender;

    private final CountDownLatch releaseSender = new CountDownLatch(1);

    @BeforeEach
    void reset() {
        clock.set(BookingTestClock.START);
        bookingRepository.deleteAllInBatch();
    }

    @AfterEach
    void unblockSender() {
        releaseSender.countDown();
    }

    private Long hold(LocalTime startTime) {
        return bookingService
                .hold(new HoldRequest(1L, DATE, startTime, "Ann Patient"))
                .id();
    }

    @Test
    void confirmationIsSentAfterCommitOnNotificationThread() {
        Long bookingId = hold(LocalTime.of(9, 0));
        AtomicReference<String> senderThread = new AtomicReference<>();
        AtomicReference<BookingStatus> committedStatus = new AtomicReference<>();
        doAnswer(invocation -> {
                    senderThread.set(Thread.currentThread().getName());
                    committedStatus.set(
                            bookingRepository.findById(bookingId).orElseThrow().getStatus());
                    return null;
                })
                .when(notificationSender)
                .sendConfirmation(any());

        bookingService.confirm(bookingId);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(notificationSender)
                        .sendConfirmation(new BookingConfirmedEvent(bookingId, 1L, DATE, LocalTime.of(9, 0))));
        assertThat(senderThread.get())
                .startsWith(BookingConfig.NOTIFICATION_THREAD_PREFIX)
                .isNotEqualTo(Thread.currentThread().getName());
        assertThat(committedStatus.get()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmReturnsWithoutWaitingForTheNotification() throws InterruptedException {
        Long bookingId = hold(LocalTime.of(9, 30));
        CountDownLatch senderStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
                    senderStarted.countDown();
                    releaseSender.await(10, TimeUnit.SECONDS);
                    return null;
                })
                .when(notificationSender)
                .sendConfirmation(any());

        BookingView confirmed = bookingService.confirm(bookingId);

        assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(releaseSender.getCount()).isEqualTo(1);
    }

    @Test
    void confirmationIsNotSentWhenTheConfirmTransactionRollsBack() {
        Long bookingId = hold(LocalTime.of(10, 0));

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.confirm(bookingId);
            status.setRollbackOnly();
        });

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(notificationSender, never()).sendConfirmation(any()));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirmationIsNotSentWhenConfirmFails() {
        Long bookingId = hold(LocalTime.of(10, 30));
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> bookingService.confirm(bookingId)).isInstanceOf(HoldExpiredException.class);

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(notificationSender, never()).sendConfirmation(any()));
    }
}
