package com.app.backend.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private final AtomicReference<Instant> current;

    public MutableClock(Instant initial) {
        this.current = new AtomicReference<>(initial);
    }

    public void setInstant(Instant instant) {
        current.set(instant);
    }

    public void advance(Duration duration) {
        current.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(current.get(), zone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
