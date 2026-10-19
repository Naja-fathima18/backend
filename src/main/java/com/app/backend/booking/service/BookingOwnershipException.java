package com.app.backend.booking.service;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class BookingOwnershipException extends ApiException {

    public BookingOwnershipException(Long bookingId) {
        super(HttpStatus.FORBIDDEN, "Booking " + bookingId + " belongs to another patient");
    }
}
