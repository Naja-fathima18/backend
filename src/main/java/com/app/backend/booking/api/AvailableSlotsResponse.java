package com.app.backend.booking.api;

import java.time.LocalDate;
import java.util.List;

public record AvailableSlotsResponse(Long doctorId, LocalDate date, List<SlotResponse> slots) {}
