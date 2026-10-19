package com.app.backend.booking.domain;

import java.time.Instant;

public record Slot(Instant startTime, Instant endTime) {}
