package com.app.backend.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BookingTest {

    private static final Instant EXPIRES_AT = Instant.parse("2030-01-10T08:05:00Z");

    private Booking newHold() {
        return Booking.hold(1L, LocalDate.of(2030, 1, 10), LocalTime.of(9, 0), "Jane Doe", EXPIRES_AT);
    }

    @Test
    void holdOccupiesTheSlotKey() {
        Booking booking = newHold();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking.getActiveSlotKey()).isEqualTo("1|2030-01-10|09:00");
    }

    @Test
    void holdExpiresExactlyAtExpiresAt() {
        Booking booking = newHold();

        assertThat(booking.isHoldExpiredAt(EXPIRES_AT.minusMillis(1))).isFalse();
        assertThat(booking.isHoldExpiredAt(EXPIRES_AT)).isTrue();
    }

    @Test
    void confirmKeepsTheSlotKey() {
        Booking booking = newHold();

        booking.confirm(EXPIRES_AT.minusSeconds(1));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getActiveSlotKey()).isEqualTo("1|2030-01-10|09:00");
        assertThat(booking.isHoldExpiredAt(EXPIRES_AT.plusSeconds(60))).isFalse();
    }

    @Test
    void confirmRejectsExpiredHold() {
        Booking booking = newHold();

        assertThatThrownBy(() -> booking.confirm(EXPIRES_AT)).isInstanceOf(HoldExpiredException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirmRejectsAlreadyConfirmedBooking() {
        Booking booking = newHold();
        booking.confirm(EXPIRES_AT.minusSeconds(1));

        assertThatThrownBy(() -> booking.confirm(EXPIRES_AT.minusSeconds(1)))
                .isInstanceOf(BookingStateConflictException.class);
    }

    @Test
    void expireReleasesTheSlotKey() {
        Booking booking = newHold();

        booking.expire();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getActiveSlotKey()).isNull();
    }

    @Test
    void cancelReleasesTheSlotKeyOfConfirmedBooking() {
        Booking booking = newHold();
        booking.confirm(EXPIRES_AT.minusSeconds(1));

        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getActiveSlotKey()).isNull();
    }

    @Test
    void cancelRejectsBookingThatIsOnlyHeld() {
        Booking booking = newHold();

        assertThatThrownBy(booking::cancel).isInstanceOf(BookingStateConflictException.class);
        assertThat(booking.getActiveSlotKey()).isNotNull();
    }
}
