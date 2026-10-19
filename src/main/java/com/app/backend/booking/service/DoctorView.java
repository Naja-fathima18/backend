package com.app.backend.booking.service;

import com.app.backend.booking.domain.Doctor;

public record DoctorView(Long id, String name, String specialty) {

    static DoctorView of(Doctor doctor) {
        return new DoctorView(doctor.getId(), doctor.getName(), doctor.getSpecialty());
    }
}
