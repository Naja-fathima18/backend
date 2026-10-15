package com.app.backend.booking.api;

import com.app.backend.booking.service.HoldRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record HoldBookingRequest(
        @NotNull @Positive Long doctorId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotBlank @Size(max = 100) String patientName) {

    HoldRequest toHoldRequest() {
        return new HoldRequest(doctorId, date, startTime, patientName);
    }
}
