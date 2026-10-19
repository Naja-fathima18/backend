package com.app.backend.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientActionRequest(@NotBlank @Size(max = 64) String patientId) {}
