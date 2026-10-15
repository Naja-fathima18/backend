package com.app.backend.booking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.booking.domain.DoctorNotFoundException;
import com.app.backend.booking.service.AvailableSlots;
import com.app.backend.booking.service.BookingService;
import com.app.backend.booking.service.DoctorService;
import com.app.backend.booking.service.DoctorView;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorController.class)
class DoctorControllerTest {

    private static final LocalDate DATE = LocalDate.of(2030, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorService doctorService;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void listsDoctorsAsPage() throws Exception {
        when(doctorService.list(any()))
                .thenReturn(new PageImpl<>(
                        List.of(new DoctorView(1L, "Dr. Alice Morgan"), new DoctorView(2L, "Dr. Brian Chen")),
                        PageRequest.of(0, 20),
                        2));

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].name").value("Dr. Brian Chen"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listsAvailableSlotStartTimes() throws Exception {
        when(bookingService.availableSlots(1L, DATE))
                .thenReturn(new AvailableSlots(1L, DATE, List.of(LocalTime.of(9, 30), LocalTime.of(10, 0))));

        mockMvc.perform(get("/api/doctors/1/slots").param("date", "2030-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(1))
                .andExpect(jsonPath("$.date").value("2030-01-10"))
                .andExpect(jsonPath("$.availableStartTimes[0]").value("09:30:00"))
                .andExpect(jsonPath("$.availableStartTimes.length()").value(2));
    }

    @Test
    void slotsOfUnknownDoctorAreNotFound() throws Exception {
        when(bookingService.availableSlots(9L, DATE)).thenThrow(new DoctorNotFoundException(9L));

        mockMvc.perform(get("/api/doctors/9/slots").param("date", "2030-01-10")).andExpect(status().isNotFound());
    }

    @Test
    void slotsRequireDate() throws Exception {
        mockMvc.perform(get("/api/doctors/1/slots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    @Test
    void slotsRejectMalformedDate() throws Exception {
        mockMvc.perform(get("/api/doctors/1/slots").param("date", "10-01-2030"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }
}
