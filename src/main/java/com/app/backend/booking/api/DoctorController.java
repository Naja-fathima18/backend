package com.app.backend.booking.api;

import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.DoctorService;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;
    private final BookingService bookingService;

    public DoctorController(DoctorService doctorService, BookingService bookingService) {
        this.doctorService = doctorService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public DoctorPageResponse list(@PageableDefault(size = 20) Pageable pageable) {
        return DoctorPageResponse.from(doctorService.list(pageable));
    }

    @GetMapping("/{doctorId}/slots")
    public AvailableSlotsResponse availableSlots(@PathVariable @Positive Long doctorId, @RequestParam LocalDate date) {
        return AvailableSlotsResponse.from(bookingService.availableSlots(doctorId, date));
    }
}
