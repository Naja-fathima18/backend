package com.app.backend.booking.service;

import com.app.backend.booking.domain.DoctorNotFoundException;
import com.app.backend.booking.persistence.DoctorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public Page<DoctorView> list(Pageable pageable) {
        Pageable byId = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        return doctorRepository.findAll(byId).map(doctor -> new DoctorView(doctor.getId(), doctor.getName()));
    }

    @Transactional(readOnly = true)
    public void requireExists(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new DoctorNotFoundException(doctorId);
        }
    }
}
