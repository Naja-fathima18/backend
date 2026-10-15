package com.app.backend.ratelimit.domain;

public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {

    public static RateLimitDecision allow(int limit, int remaining) {
        return new RateLimitDecision(true, limit, remaining, 0);
    }

    public static RateLimitDecision reject(int limit, long retryAfterSeconds) {
        return new RateLimitDecision(false, limit, 0, retryAfterSeconds);
    }
}
