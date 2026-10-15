package com.app.backend.booking.api;

import com.app.backend.booking.service.AvailableSlots;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailableSlotsResponse(Long doctorId, LocalDate date, List<LocalTime> availableStartTimes) {

    static AvailableSlotsResponse from(AvailableSlots slots) {
        return new AvailableSlotsResponse(slots.doctorId(), slots.date(), slots.startTimes());
    }
}
