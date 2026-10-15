package com.app.backend.booking.api;

import com.app.backend.booking.service.DoctorView;
import java.util.List;
import org.springframework.data.domain.Page;

public record DoctorPageResponse(List<DoctorResponse> content, int page, int size, long totalElements, int totalPages) {

    static DoctorPageResponse from(Page<DoctorView> page) {
        return new DoctorPageResponse(
                page.getContent().stream().map(DoctorResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
