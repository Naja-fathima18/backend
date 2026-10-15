package com.app.backend.booking.domain;

import com.app.backend.common.error.ConflictException;

public class BookingStateConflictException extends ConflictException {

    public BookingStateConflictException(String message) {
        super(message);
    }
}
