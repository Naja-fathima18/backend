package com.app.backend.parking.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.parking.domain.ExitBeforeEntryException;
import com.app.backend.parking.service.ParkingFeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParkingFeeController.class)
@Import(ParkingFeeService.class)
class ParkingFeeControllerTest {

    private static final String FEE_PATH = "/api/parking/fee";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsVisitFeeWithDuration() throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", "09:00").param("exit", "10:00"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json("{\"entry\":\"09:00\",\"exit\":\"10:00\",\"durationMinutes\":60,\"fee\":20}", true));
    }

    @ParameterizedTest(name = "{0}-{1} lasts {2} minutes and costs {3}")
    @CsvSource({
        "09:00, 09:30, 30, 0",
        "09:00, 10:00, 60, 20",
        "09:00, 10:01, 61, 40",
        "08:00, 17:00, 540, 100",
        "10:00, 10:00, 0, 0",
        "10:00, 10:31, 31, 20",
        "10:00, 11:01, 61, 40",
        "10:00, 15:01, 301, 100",
        "00:00, 23:59, 1439, 100"
    })
    void returnsFeeForRequiredExamplesAndBoundaries(String entry, String exit, int minutes, int fee) throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", entry).param("exit", exit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry").value(entry))
                .andExpect(jsonPath("$.exit").value(exit))
                .andExpect(jsonPath("$.durationMinutes").value(minutes))
                .andExpect(jsonPath("$.fee").value(fee));
    }

    @Test
    void rejectsExitBeforeEntry() throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", "10:00").param("exit", "09:59"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(ExitBeforeEntryException.MESSAGE))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("exit"))
                .andExpect(jsonPath("$.errors[0].message").value(ExitBeforeEntryException.MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "9:00", "24:00", "09:60", "09:00:00", "noon"})
    void rejectsMalformedEntry(String entry) throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", entry).param("exit", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(ClockTime.INVALID_TIME_MESSAGE))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("entry"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "9:00", "24:00", "09:60", "09:00:00"})
    void rejectsMalformedExit(String exit) throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", "08:00").param("exit", exit))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("exit"))
                .andExpect(jsonPath("$.errors[0].message").value(ClockTime.INVALID_TIME_MESSAGE));
    }

    @Test
    void rejectsMissingEntry() throws Exception {
        mockMvc.perform(get(FEE_PATH).param("exit", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("entry"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
    }

    @Test
    void rejectsMissingExit() throws Exception {
        mockMvc.perform(get(FEE_PATH).param("entry", "09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("exit"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
    }
}
