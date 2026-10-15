package com.app.backend.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;

public record FixedWindow(Instant start, int count) {

    public static FixedWindow openAt(Instant now) {
        return new FixedWindow(now, 1);
    }

    public boolean isExpired(Instant now, Duration length) {
        return !now.isBefore(end(length));
    }

    public FixedWindow consume(int capacity) {
        return new FixedWindow(start, Math.min(count, capacity) + 1);
    }

    public boolean isWithin(int capacity) {
        return count <= capacity;
    }

    public Instant end(Duration length) {
        return start.plus(length);
    }
}
