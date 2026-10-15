package com.app.backend.booking.domain;

import com.app.backend.common.error.NotFoundException;

public class DoctorNotFoundException extends NotFoundException {

    public DoctorNotFoundException(Long id) {
        super("Doctor " + id + " not found");
    }
}
