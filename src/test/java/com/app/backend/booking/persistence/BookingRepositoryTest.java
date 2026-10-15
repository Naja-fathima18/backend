package com.app.backend.booking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class BookingRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2031, 3, 4);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final Instant NOW = Instant.parse("2031-03-04T06:00:00Z");

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @BeforeEach
    void clearBookings() {
        bookingRepository.deleteAllInBatch();
    }

    private Booking hold(LocalTime startTime, Instant expiresAt) {
        return bookingRepository.saveAndFlush(Booking.hold(1L, DATE, startTime, "Jane Doe", expiresAt));
    }

    @Test
    void migrationSeedsDoctors() {
        assertThat(doctorRepository.count()).isGreaterThanOrEqualTo(2);
        assertThat(doctorRepository.existsById(1L)).isTrue();
    }

    @Test
    void databaseRejectsSecondActiveBookingForSameSlot() {
        hold(NINE, NOW.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> hold(NINE, NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void releasedBookingsDoNotBlockTheSlot() {
        Booking expired = hold(NINE, NOW.plus(Duration.ofMinutes(5)));
        expired.expire();
        bookingRepository.saveAndFlush(expired);
        Booking held = hold(NINE, NOW.plus(Duration.ofMinutes(5)));
        held.confirm(NOW);
        held.cancel();
        bookingRepository.saveAndFlush(held);

        Booking third = hold(NINE, NOW.plus(Duration.ofMinutes(5)));

        assertThat(third.getId()).isNotNull();
        assertThat(bookingRepository.countByActiveSlotKey(Booking.slotKey(1L, DATE, NINE)))
                .isEqualTo(1);
    }

    @Test
    void takenStartTimesIncludeConfirmedAndLiveHoldsOnly() {
        Booking confirmed = hold(LocalTime.of(10, 0), NOW.plus(Duration.ofMinutes(5)));
        confirmed.confirm(NOW);
        bookingRepository.saveAndFlush(confirmed);
        hold(LocalTime.of(11, 0), NOW.plus(Duration.ofMinutes(5)));
        hold(LocalTime.of(12, 0), NOW);

        assertThat(bookingRepository.findTakenStartTimes(1L, DATE, NOW))
                .containsExactlyInAnyOrder(LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(bookingRepository.findTakenStartTimes(2L, DATE, NOW)).isEmpty();
    }

    @Test
    void expireHoldForSlotReleasesOnlyAnOverdueHold() {
        Booking live = hold(NINE, NOW.plus(Duration.ofMinutes(5)));
        Booking overdue = hold(LocalTime.of(9, 30), NOW.minusSeconds(1));

        int releasedLive = bookingRepository.expireHoldForSlot(live.getActiveSlotKey(), NOW);
        int releasedOverdue = bookingRepository.expireHoldForSlot(overdue.getActiveSlotKey(), NOW);

        assertThat(releasedLive).isZero();
        assertThat(releasedOverdue).isEqualTo(1);
        Booking reloaded = bookingRepository.findById(overdue.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(reloaded.getActiveSlotKey()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(overdue.getVersion() + 1);
    }

    @Test
    void expireOverdueHoldsLeavesLiveHoldsAndConfirmedBookingsAlone() {
        Booking live = hold(NINE, NOW.plus(Duration.ofMinutes(5)));
        hold(LocalTime.of(9, 30), NOW);
        hold(LocalTime.of(10, 0), NOW.minus(Duration.ofHours(1)));

        int expired = bookingRepository.expireOverdueHolds(NOW);

        assertThat(expired).isEqualTo(2);
        assertThat(bookingRepository.findById(live.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
    }
}
