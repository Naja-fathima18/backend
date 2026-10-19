package com.app.backend.booking.api;

import com.app.backend.booking.domain.Slot;
import java.time.Instant;

public record SlotResponse(Instant startTime, Instant endTime) {

    static SlotResponse from(Slot slot) {
        return new SlotResponse(slot.startTime(), slot.endTime());
    }
}
