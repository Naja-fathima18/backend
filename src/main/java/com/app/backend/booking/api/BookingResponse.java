package com.app.backend.booking.api;

import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.service.BookingView;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long doctorId,
        Instant startTime,
        Instant endTime,
        String patientId,
        BookingStatus status,
        Instant holdExpiresAt) {

    static BookingResponse from(BookingView view) {
        return new BookingResponse(
                view.id(),
                view.doctorId(),
                view.startTime(),
                view.endTime(),
                view.patientId(),
                view.status(),
                view.holdExpiresAt());
    }
}
