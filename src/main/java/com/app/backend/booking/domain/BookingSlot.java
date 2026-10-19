package com.app.backend.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "booking_slots")
public class BookingSlot {

    @EmbeddedId
    private BookingSlotId id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BookingSlot() {}

    public BookingSlotId getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }
}
