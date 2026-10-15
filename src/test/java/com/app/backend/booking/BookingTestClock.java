package com.app.backend.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class BookingTestClock {

    public static final Instant START = Instant.parse("2032-06-01T06:00:00Z");
    public static final MutableClock CLOCK = new MutableClock(START, ZoneOffset.UTC);

    private BookingTestClock() {}

    public static Clock bookingClock() {
        return CLOCK;
    }
}
