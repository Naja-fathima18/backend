package com.app.backend.booking.service;

import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import java.time.Instant;

public record BookingView(
        Long id,
        Long doctorId,
        Instant startTime,
        Instant endTime,
        String patientId,
        BookingStatus status,
        Instant holdExpiresAt) {

    static BookingView of(Booking booking, Instant now) {
        return new BookingView(
                booking.getId(),
                booking.getDoctorId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getPatientId(),
                booking.statusAt(now),
                booking.getHoldExpiresAt());
    }
}
