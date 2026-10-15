package com.app.backend.booking.domain;

import com.app.backend.common.error.NotFoundException;

public class BookingNotFoundException extends NotFoundException {

    public BookingNotFoundException(Long id) {
        super("Booking " + id + " not found");
    }
}
