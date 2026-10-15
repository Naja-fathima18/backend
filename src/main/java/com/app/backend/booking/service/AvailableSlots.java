package com.app.backend.booking.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailableSlots(Long doctorId, LocalDate date, List<LocalTime> startTimes) {}
