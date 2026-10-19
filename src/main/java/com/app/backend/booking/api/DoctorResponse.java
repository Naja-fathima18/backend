package com.app.backend.booking.api;

import com.app.backend.booking.service.DoctorView;

public record DoctorResponse(Long id, String name, String specialty) {

    static DoctorResponse from(DoctorView view) {
        return new DoctorResponse(view.id(), view.name(), view.specialty());
    }
}
