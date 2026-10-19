package com.app.backend.booking.api;

import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.BookingView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> hold(@Valid @RequestBody HoldBookingRequest request) {
        BookingView booking =
                bookingService.hold(request.doctorId(), request.startTime().toInstant(), request.patientId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(booking.id())
                .toUri();
        return ResponseEntity.created(location).body(BookingResponse.from(booking));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable @Positive Long id) {
        return BookingResponse.from(bookingService.get(id));
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable @Positive Long id, @Valid @RequestBody PatientActionRequest request) {
        return BookingResponse.from(bookingService.confirm(id, request.patientId()));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable @Positive Long id, @Valid @RequestBody PatientActionRequest request) {
        return BookingResponse.from(bookingService.cancel(id, request.patientId()));
    }
}
