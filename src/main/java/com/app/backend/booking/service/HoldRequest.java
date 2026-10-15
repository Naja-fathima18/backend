package com.app.backend.booking.service;

import java.time.LocalDate;
import java.time.LocalTime;

public record HoldRequest(Long doctorId, LocalDate date, LocalTime startTime, String patientName) {}
