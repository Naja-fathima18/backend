package com.app.backend.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class BookingSlotId implements Serializable {

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    protected BookingSlotId() {}

    public BookingSlotId(Long doctorId, Instant startTime) {
        this.doctorId = doctorId;
        this.startTime = startTime;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BookingSlotId that)) {
            return false;
        }
        return Objects.equals(doctorId, that.doctorId) && Objects.equals(startTime, that.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(doctorId, startTime);
    }
}
