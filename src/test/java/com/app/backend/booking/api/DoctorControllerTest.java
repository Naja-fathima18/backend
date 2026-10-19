package com.app.backend.booking.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.booking.domain.Slot;
import com.app.backend.booking.service.DoctorService;
import com.app.backend.booking.service.DoctorView;
import java.time.Instant;
import java.time.LocalDate;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorService doctorService;

    @Test
    void listsDoctorsAsPage() throws Exception {
        when(doctorService.list(any()))
                .thenReturn(new PageImpl<>(
                        List.of(new DoctorView(1L, "Dr. Asha Menon", "General Practice")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Dr. Asha Menon"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void returnsAvailableSlotsForDay() throws Exception {
        Instant start = Instant.parse("2030-01-07T09:00:00Z");
        when(doctorService.availableSlots(1L, LocalDate.of(2030, 1, 7)))
                .thenReturn(List.of(new Slot(start, start.plusSeconds(1800))));

        mockMvc.perform(get("/api/doctors/1/slots").param("date", "2030-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(1))
                .andExpect(jsonPath("$.date").value("2030-01-07"))
                .andExpect(jsonPath("$.slots[0].startTime").value("2030-01-07T09:00:00Z"))
                .andExpect(jsonPath("$.slots[0].endTime").value("2030-01-07T09:30:00Z"));
    }

    @Test
    void rejectsMissingOrMalformedDate() throws Exception {
        mockMvc.perform(get("/api/doctors/1/slots")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/doctors/1/slots").param("date", "07-01-2030")).andExpect(status().isBadRequest());

        verifyNoInteractions(doctorService);
    }
}
