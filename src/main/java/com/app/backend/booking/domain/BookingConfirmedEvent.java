package com.app.backend.booking.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingConfirmedEvent(Long bookingId, Long doctorId, LocalDate date, LocalTime startTime) {}
