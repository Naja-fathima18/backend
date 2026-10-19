package com.app.backend.booking.service;

import java.time.Instant;

public record BookingConfirmedEvent(Long bookingId, Long doctorId, Instant startTime) {}
