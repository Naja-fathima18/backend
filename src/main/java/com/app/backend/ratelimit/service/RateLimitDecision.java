package com.app.backend.ratelimit.service;

public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {}
