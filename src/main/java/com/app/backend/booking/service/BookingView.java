package com.app.backend.booking.service;

import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingView(
        Long id, Long doctorId, LocalDate date, LocalTime startTime, BookingStatus status, Instant expiresAt) {

    static BookingView of(Booking booking) {
        return new BookingView(
                booking.getId(),
                booking.getDoctorId(),
                booking.getSlotDate(),
                booking.getStartTime(),
                booking.getStatus(),
                booking.getExpiresAt());
    }
}
