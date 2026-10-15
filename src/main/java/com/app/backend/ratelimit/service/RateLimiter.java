package com.app.backend.ratelimit.service;

import com.app.backend.ratelimit.config.RateLimitConfig;
import com.app.backend.ratelimit.config.RateLimitProperties;
import com.app.backend.ratelimit.domain.FixedWindow;
import com.app.backend.ratelimit.domain.RateLimitDecision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RateLimiter {

    private final ConcurrentMap<String, FixedWindow> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int capacity;
    private final Duration window;

    public RateLimiter(RateLimitProperties properties, @Qualifier(RateLimitConfig.CLOCK_BEAN) Clock clock) {
        this.clock = clock;
        this.capacity = properties.capacity();
        this.window = properties.window();
    }

    public RateLimitDecision tryConsume(String clientKey) {
        Instant now = clock.instant();
        FixedWindow current = windows.compute(clientKey, (key, existing) -> nextWindow(existing, now));
        if (current.isWithin(capacity)) {
            return RateLimitDecision.allow(capacity, capacity - current.count());
        }
        return RateLimitDecision.reject(capacity, retryAfterSeconds(current, now));
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void evictExpiredWindows() {
        Instant now = clock.instant();
        for (String key : windows.keySet()) {
            windows.computeIfPresent(key, (k, existing) -> existing.isExpired(now, window) ? null : existing);
        }
    }

    int trackedClients() {
        return windows.size();
    }

    private FixedWindow nextWindow(FixedWindow existing, Instant now) {
        if (existing == null || existing.isExpired(now, window)) {
            return FixedWindow.openAt(now);
        }
        return existing.consume(capacity);
    }

    private long retryAfterSeconds(FixedWindow current, Instant now) {
        Duration wait = Duration.between(now, current.end(window));
        long roundedUpSeconds = wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0);
        return Math.max(1, roundedUpSeconds);
    }
}
