package com.app.backend.booking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.booking.domain.BookingNotFoundException;
import com.app.backend.booking.domain.BookingStateConflictException;
import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.domain.HoldExpiredException;
import com.app.backend.booking.domain.SlotUnavailableException;
import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.BookingView;
import com.app.backend.booking.service.HoldRequest;
import com.app.backend.common.error.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final LocalDate DATE = LocalDate.of(2030, 1, 10);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-10T07:05:00Z");
    private static final String HOLD_BODY = """
            {"doctorId": 1, "date": "2030-01-10", "startTime": "09:00", "patientName": "Jane Doe"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private BookingView view(BookingStatus status) {
        return new BookingView(5L, 1L, DATE, NINE, status, status == BookingStatus.HELD ? EXPIRES_AT : null);
    }

    @Test
    void holdReturnsCreatedWithLocationAndExpiry() throws Exception {
        when(bookingService.hold(new HoldRequest(1L, DATE, NINE, "Jane Doe"))).thenReturn(view(BookingStatus.HELD));

        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOLD_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/5"))
                .andExpect(jsonPath("$.bookingId").value(5))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-10T07:05:00Z"))
                .andExpect(jsonPath("$.patientName").doesNotExist());
    }

    @Test
    void holdOfUnavailableSlotIsConflict() throws Exception {
        when(bookingService.hold(any())).thenThrow(new SlotUnavailableException());

        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOLD_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slot is not available"));
    }

    @Test
    void holdWithOffGridStartIsBadRequest() throws Exception {
        when(bookingService.hold(any())).thenThrow(new BadRequestException("startTime", "must align"));

        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOLD_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
    }

    @Test
    void holdValidatesRequestBody() throws Exception {
        String body = """
                {"doctorId": 0, "date": null, "startTime": "09:00", "patientName": " "}
                """;

        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        verify(bookingService, never()).hold(any());
    }

    @Test
    void holdWithMalformedTimeIsBadRequest() throws Exception {
        String body = """
                {"doctorId": 1, "date": "2030-01-10", "startTime": "nine", "patientName": "Jane"}
                """;

        mockMvc.perform(post("/api/bookings/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmReturnsConfirmedBooking() throws Exception {
        when(bookingService.confirm(5L)).thenReturn(view(BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/bookings/5/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOfExpiredHoldIsConflict() throws Exception {
        when(bookingService.confirm(5L)).thenThrow(new HoldExpiredException());

        mockMvc.perform(post("/api/bookings/5/confirm")).andExpect(status().isConflict());
    }

    @Test
    void confirmOfUnknownBookingIsNotFound() throws Exception {
        when(bookingService.confirm(5L)).thenThrow(new BookingNotFoundException(5L));

        mockMvc.perform(post("/api/bookings/5/confirm")).andExpect(status().isNotFound());
    }

    @Test
    void cancelReturnsCancelledBooking() throws Exception {
        when(bookingService.cancel(5L)).thenReturn(view(BookingStatus.CANCELLED));

        mockMvc.perform(post("/api/bookings/5/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOfNonConfirmedBookingIsConflict() throws Exception {
        when(bookingService.cancel(5L)).thenThrow(new BookingStateConflictException("not confirmed"));

        mockMvc.perform(post("/api/bookings/5/cancel")).andExpect(status().isConflict());
    }

    @Test
    void cancelOfUnknownBookingIsNotFound() throws Exception {
        when(bookingService.cancel(5L)).thenThrow(new BookingNotFoundException(5L));

        mockMvc.perform(post("/api/bookings/5/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void getReturnsBooking() throws Exception {
        when(bookingService.get(5L)).thenReturn(view(BookingStatus.HELD));

        mockMvc.perform(get("/api/bookings/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(5));
    }

    @Test
    void nonPositiveIdIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/bookings/0/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }
}
