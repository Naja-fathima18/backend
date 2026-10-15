package com.app.backend.parking.api;

public record VisitFeeResponse(String entry, String exit, int durationMinutes, int fee) {}
