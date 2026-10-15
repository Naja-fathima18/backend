package com.app.backend.booking.domain;

import com.app.backend.common.error.ConflictException;

public class SlotUnavailableException extends ConflictException {

    public SlotUnavailableException() {
        super("Slot is not available");
    }
}
