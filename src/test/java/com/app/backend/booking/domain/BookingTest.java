package com.app.backend.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.common.error.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingTest {

    private static final Instant START = Instant.parse("2030-01-07T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2030-01-07T08:05:00Z");
    private static final Slot SLOT = new Slot(START, START.plus(SlotSchedule.SLOT_LENGTH));

    @Test
    void newHoldIsHeldUntilItsExpiry() {
        Booking booking = Booking.hold(1L, SLOT, "alice", EXPIRES);

        assertThat(booking.statusAt(EXPIRES.minusMillis(1))).isEqualTo(BookingStatus.HELD);
        assertThat(booking.statusAt(EXPIRES)).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.isHoldExpiredAt(EXPIRES.minusMillis(1))).isFalse();
        assertThat(booking.isHoldExpiredAt(EXPIRES)).isTrue();
    }

    @Test
    void confirmedBookingNeverExpires() {
        Booking booking = Booking.hold(1L, SLOT, "alice", EXPIRES);

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getHoldExpiresAt()).isNull();
        assertThat(booking.statusAt(EXPIRES.plusSeconds(3600))).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void onlyConfirmedBookingCanBeCancelled() {
        Booking held = Booking.hold(1L, SLOT, "alice", EXPIRES);
        Booking confirmed = Booking.hold(1L, SLOT, "alice", EXPIRES);
        confirmed.confirm();

        confirmed.cancel();

        assertThatThrownBy(held::cancel).isInstanceOf(ConflictException.class);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void onlyHeldBookingCanBeConfirmedOrExpired() {
        Booking expired = Booking.hold(1L, SLOT, "alice", EXPIRES);
        expired.expire();

        assertThatThrownBy(expired::confirm).isInstanceOf(ConflictException.class);
        assertThatThrownBy(expired::expire).isInstanceOf(ConflictException.class);
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void belongsOnlyToTheHoldingPatient() {
        Booking booking = Booking.hold(1L, SLOT, "alice", EXPIRES);

        assertThat(booking.belongsTo("alice")).isTrue();
        assertThat(booking.belongsTo("bob")).isFalse();
    }
}
