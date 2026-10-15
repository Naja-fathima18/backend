package com.app.backend.ratelimit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.MutableClock;
import com.app.backend.ratelimit.config.RateLimitProperties;
import com.app.backend.ratelimit.domain.RateLimitDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final int CAPACITY = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RateLimiter rateLimiter =
            new RateLimiter(new RateLimitProperties(CAPACITY, WINDOW, "X-API-Key", List.of("/api/quotes/**")), clock);

    @Test
    void allowsRequestsUpToCapacityAndCountsDownRemaining() {
        RateLimitDecision first = rateLimiter.tryConsume("key-a");
        RateLimitDecision second = rateLimiter.tryConsume("key-a");
        RateLimitDecision third = rateLimiter.tryConsume("key-a");

        assertThat(List.of(first, second, third)).allMatch(RateLimitDecision::allowed);
        assertThat(first.remaining()).isEqualTo(2);
        assertThat(second.remaining()).isEqualTo(1);
        assertThat(third.remaining()).isZero();
        assertThat(third.limit()).isEqualTo(CAPACITY);
    }

    @Test
    void rejectsRequestOverCapacityWithTimeUntilWindowEnds() {
        consume("key-a", CAPACITY);
        clock.advance(Duration.ofSeconds(20));

        RateLimitDecision rejected = rateLimiter.tryConsume("key-a");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void roundsRetryAfterUpToWholeSeconds() {
        consume("key-a", CAPACITY);
        clock.advance(Duration.ofSeconds(20).plusMillis(1));

        RateLimitDecision rejected = rateLimiter.tryConsume("key-a");

        assertThat(rejected.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void reportsAtLeastOneSecondRetryAfterJustBeforeWindowEnds() {
        consume("key-a", CAPACITY);
        clock.advance(WINDOW.minusMillis(1));

        RateLimitDecision rejected = rateLimiter.tryConsume("key-a");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void keepsRejectingWhileWindowIsOpen() {
        consume("key-a", CAPACITY);

        RateLimitDecision firstRejection = rateLimiter.tryConsume("key-a");
        RateLimitDecision secondRejection = rateLimiter.tryConsume("key-a");

        assertThat(firstRejection.allowed()).isFalse();
        assertThat(secondRejection.allowed()).isFalse();
    }

    @Test
    void allowsAgainOnceWindowHasPassed() {
        consume("key-a", CAPACITY + 1);
        clock.advance(WINDOW);

        RateLimitDecision decision = rateLimiter.tryConsume("key-a");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(CAPACITY - 1);
    }

    @Test
    void limitsEachKeyIndependently() {
        consume("key-a", CAPACITY);

        RateLimitDecision exhausted = rateLimiter.tryConsume("key-a");
        RateLimitDecision other = rateLimiter.tryConsume("key-b");

        assertThat(exhausted.allowed()).isFalse();
        assertThat(other.allowed()).isTrue();
        assertThat(other.remaining()).isEqualTo(CAPACITY - 1);
    }

    @Test
    void evictsOnlyExpiredWindows() {
        consume("idle", 1);
        clock.advance(Duration.ofSeconds(30));
        consume("active", 1);
        clock.advance(Duration.ofSeconds(30));

        rateLimiter.evictExpiredWindows();

        assertThat(rateLimiter.trackedClients()).isEqualTo(1);
        assertThat(rateLimiter.tryConsume("active").remaining()).isEqualTo(CAPACITY - 2);
    }

    @Test
    void letsExactlyCapacityThroughUnderConcurrentRequestsForSameKey() throws Exception {
        int threads = 50;
        RateLimiter limiter =
                new RateLimiter(new RateLimitProperties(10, WINDOW, "X-API-Key", List.of("/api/quotes/**")), clock);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RateLimitDecision>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryConsume("shared");
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<RateLimitDecision> result : results) {
                if (result.get(10, TimeUnit.SECONDS).allowed()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(10);
        }
    }

    private void consume(String key, int times) {
        for (int i = 0; i < times; i++) {
            rateLimiter.tryConsume(key);
        }
    }
}
