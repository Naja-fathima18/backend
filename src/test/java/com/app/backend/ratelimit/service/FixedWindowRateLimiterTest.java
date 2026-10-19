package com.app.backend.ratelimit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.ratelimit.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsRequestsUpToTheLimitAndCountsDownRemaining() {
        List<RateLimitDecision> decisions = IntStream.range(0, 3)
                .mapToObj(i -> rateLimiter.tryAcquire("key"))
                .toList();

        assertThat(decisions).allMatch(RateLimitDecision::allowed);
        assertThat(decisions).extracting(RateLimitDecision::remaining).containsExactly(2, 1, 0);
        assertThat(decisions).extracting(RateLimitDecision::limit).containsOnly(3);
    }

    @Test
    void rejectsRequestOverTheLimitWithWaitUntilWindowEnds() {
        acquire("key", 3);
        clock.advance(Duration.ofSeconds(20));

        RateLimitDecision decision = rateLimiter.tryAcquire("key");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void roundsRetryAfterUpToWholeSeconds() {
        acquire("key", 3);
        clock.advance(Duration.ofMillis(59_001));

        RateLimitDecision decision = rateLimiter.tryAcquire("key");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void keepsRejectingWhileWindowIsOpen() {
        acquire("key", 5);
        clock.advance(Duration.ofSeconds(59));

        RateLimitDecision decision = rateLimiter.tryAcquire("key");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void allowsAgainOnceWindowHasPassed() {
        acquire("key", 4);
        clock.advance(Duration.ofMinutes(1));

        RateLimitDecision decision = rateLimiter.tryAcquire("key");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(2);
    }

    @Test
    void tracksEachKeyIndependently() {
        acquire("first", 4);

        RateLimitDecision decision = rateLimiter.tryAcquire("second");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(2);
        assertThat(rateLimiter.tryAcquire("first").allowed()).isFalse();
    }

    @Test
    void evictsCountersOfKeysWhoseWindowHasEnded() {
        IntStream.range(0, 100).forEach(i -> rateLimiter.tryAcquire("stale-" + i));
        clock.advance(Duration.ofMinutes(1));

        rateLimiter.tryAcquire("fresh");

        assertThat(rateLimiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void keepsCountersOfKeysWhoseWindowIsStillOpen() {
        rateLimiter.tryAcquire("early");
        clock.advance(Duration.ofSeconds(30));
        acquire("late", 3);
        clock.advance(Duration.ofSeconds(30));

        rateLimiter.tryAcquire("fresh");

        assertThat(rateLimiter.trackedKeyCount()).isEqualTo(2);
        assertThat(rateLimiter.tryAcquire("late").allowed()).isFalse();
    }

    @Test
    void grantsExactlyLimitPermitsUnderConcurrentRequestsForOneKey() throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(10, Duration.ofMinutes(1), clock);
        int threads = 32;
        int requestsPerThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    int granted = 0;
                    for (int r = 0; r < requestsPerThread; r++) {
                        if (limiter.tryAcquire("shared").allowed()) {
                            granted++;
                        }
                    }
                    return granted;
                }));
            }
            start.countDown();
            int totalGranted = 0;
            for (Future<Integer> result : results) {
                totalGranted += result.get(10, TimeUnit.SECONDS);
            }

            assertThat(totalGranted).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsLimitBelowOne() {
        assertThatThrownBy(() -> new FixedWindowRateLimiter(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> new FixedWindowRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void acquire(String key, int times) {
        IntStream.range(0, times).forEach(i -> rateLimiter.tryAcquire(key));
    }
}
