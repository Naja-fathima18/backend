package com.app.backend.booking.domain;

import com.app.backend.common.error.ConflictException;

public class HoldExpiredException extends ConflictException {

    public HoldExpiredException() {
        super("Hold has expired and the slot has been released");
    }
}
