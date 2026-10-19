package com.app.backend.booking.service;

import com.app.backend.common.error.ConflictException;

public class HoldExpiredException extends ConflictException {

    public HoldExpiredException(Long bookingId) {
        super("Hold for booking " + bookingId + " has expired");
    }
}
