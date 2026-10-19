package com.app.backend.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record HoldBookingRequest(
        @NotNull @Positive Long doctorId,
        @NotNull OffsetDateTime startTime,
        @NotBlank @Size(max = 64) String patientId) {}
