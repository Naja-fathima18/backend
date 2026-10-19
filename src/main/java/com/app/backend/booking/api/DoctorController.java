package com.app.backend.booking.api;

import com.app.backend.booking.service.DoctorService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public PageResponse<DoctorResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(doctorService.list(pageable).map(DoctorResponse::from));
    }

    @GetMapping("/{id}/slots")
    public AvailableSlotsResponse slots(
            @PathVariable @Positive Long id,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<SlotResponse> slots = doctorService.availableSlots(id, date).stream()
                .map(SlotResponse::from)
                .toList();
        return new AvailableSlotsResponse(id, date, slots);
    }
}
