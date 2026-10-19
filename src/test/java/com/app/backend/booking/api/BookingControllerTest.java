package com.app.backend.booking.api;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.service.BookingOwnershipException;
import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.BookingView;
import com.app.backend.booking.service.HoldExpiredException;
import com.app.backend.common.error.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final Instant START = Instant.parse("2030-01-07T10:00:00Z");
    private static final BookingView HELD = new BookingView(
            7L, 1L, START, START.plusSeconds(1800), "alice", BookingStatus.HELD, Instant.parse("2030-01-07T08:05:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void holdReturnsCreatedWithLocation() throws Exception {
        when(bookingService.hold(1L, START, "alice")).thenReturn(HELD);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1,\"startTime\":\"2030-01-07T10:00:00Z\",\"patientId\":\"alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.startTime").value("2030-01-07T10:00:00Z"))
                .andExpect(jsonPath("$.endTime").value("2030-01-07T10:30:00Z"))
                .andExpect(jsonPath("$.holdExpiresAt").value("2030-01-07T08:05:00Z"));
    }

    @Test
    void holdRejectsMissingAndBlankFields() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("doctorId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("startTime")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("patientId")));

        verifyNoInteractions(bookingService);
    }

    @Test
    void holdRejectsUnparseableStartTime() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1,\"startTime\":\"tomorrow\",\"patientId\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void holdMapsInvalidSlotToBadRequest() throws Exception {
        when(bookingService.hold(anyLong(), any(), anyString()))
                .thenThrow(new BadRequestException("startTime", "must not be in the past"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1,\"startTime\":\"2020-01-07T10:00:00Z\",\"patientId\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
    }

    @Test
    void confirmReturnsConfirmedBooking() throws Exception {
        BookingView confirmed =
                new BookingView(7L, 1L, START, START.plusSeconds(1800), "alice", BookingStatus.CONFIRMED, null);
        when(bookingService.confirm(7L, "alice")).thenReturn(confirmed);

        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOfExpiredHoldIsConflict() throws Exception {
        when(bookingService.confirm(7L, "alice")).thenThrow(new HoldExpiredException(7L));

        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"alice\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmByAnotherPatientIsForbidden() throws Exception {
        when(bookingService.confirm(7L, "bob")).thenThrow(new BookingOwnershipException(7L));

        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"bob\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmRequiresPatientId() throws Exception {
        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("patientId"));

        verifyNoInteractions(bookingService);
    }

    @Test
    void cancelRejectsNonPositiveId() throws Exception {
        mockMvc.perform(post("/api/bookings/0/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void getReturnsBooking() throws Exception {
        when(bookingService.get(7L)).thenReturn(HELD);

        mockMvc.perform(get("/api/bookings/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value("alice"));
    }
}
