package com.app.backend.ratelimit.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class FixedWindowRateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> nextEvictionAt;

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (window == null || !window.isPositive()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock;
        this.nextEvictionAt = new AtomicReference<>(clock.instant().plus(window));
    }

    public RateLimitDecision tryAcquire(String apiKey) {
        Instant now = clock.instant();
        evictExpiredCountersIfDue(now);
        WindowCounter counter = counters.compute(apiKey, (key, current) -> countRequest(current, now));
        return decide(counter, now);
    }

    int trackedKeyCount() {
        return counters.size();
    }

    private WindowCounter countRequest(WindowCounter current, Instant now) {
        if (current == null || current.hasEnded(now)) {
            return new WindowCounter(now.plus(window), 1);
        }
        return current.increment(limit + 1);
    }

    private RateLimitDecision decide(WindowCounter counter, Instant now) {
        if (counter.count() <= limit) {
            return new RateLimitDecision(true, limit, limit - counter.count(), 0);
        }
        return new RateLimitDecision(false, limit, 0, secondsUntil(counter.endsAt(), now));
    }

    private void evictExpiredCountersIfDue(Instant now) {
        Instant due = nextEvictionAt.get();
        if (now.isBefore(due) || !nextEvictionAt.compareAndSet(due, now.plus(window))) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().hasEnded(now));
    }

    private static long secondsUntil(Instant end, Instant now) {
        Duration wait = Duration.between(now, end);
        long seconds = wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    private record WindowCounter(Instant endsAt, int count) {

        boolean hasEnded(Instant now) {
            return !now.isBefore(endsAt);
        }

        WindowCounter increment(int ceiling) {
            return new WindowCounter(endsAt, Math.min(count + 1, ceiling));
        }
    }
}
