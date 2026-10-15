package com.app.backend.booking.api;

import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.service.BookingView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingResponse(
        Long bookingId, Long doctorId, LocalDate date, LocalTime startTime, BookingStatus status, Instant expiresAt) {

    static BookingResponse from(BookingView view) {
        return new BookingResponse(
                view.id(), view.doctorId(), view.date(), view.startTime(), view.status(), view.expiresAt());
    }
}
