package com.app.backend.booking;

import com.app.backend.booking.service.BookingConfirmationNotifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "booking.expiry-sweep.enabled=false")
@Import(BookingIntegrationTestSupport.TestClockConfig.class)
abstract class BookingIntegrationTestSupport {

    static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");
    static final String DAY = "2030-01-07";
    static final String TEN_AM = "2030-01-07T10:00:00Z";
    static final long DOCTOR_ID = 1L;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected BookingConfirmationNotifier notifier;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("delete from booking_slots");
        jdbcTemplate.update("delete from bookings");
        clock.setInstant(NOW);
    }

    protected ResponseEntity<Map> hold(String startTime, String patientId) {
        return restTemplate.postForEntity(
                "/api/bookings",
                Map.of("doctorId", DOCTOR_ID, "startTime", startTime, "patientId", patientId),
                Map.class);
    }

    protected ResponseEntity<Map> confirm(Object bookingId, String patientId) {
        return restTemplate.postForEntity(
                "/api/bookings/{id}/confirm", Map.of("patientId", patientId), Map.class, bookingId);
    }

    protected ResponseEntity<Map> cancel(Object bookingId, String patientId) {
        return restTemplate.postForEntity(
                "/api/bookings/{id}/cancel", Map.of("patientId", patientId), Map.class, bookingId);
    }

    protected ResponseEntity<Map> getBooking(Object bookingId) {
        return restTemplate.getForEntity("/api/bookings/{id}", Map.class, bookingId);
    }

    @SuppressWarnings("unchecked")
    protected List<String> availableStartTimes(String date) {
        ResponseEntity<Map> response =
                restTemplate.getForEntity("/api/doctors/{id}/slots?date={date}", Map.class, DOCTOR_ID, date);
        List<Map<String, Object>> slots =
                (List<Map<String, Object>>) response.getBody().get("slots");
        return slots.stream().map(slot -> (String) slot.get("startTime")).toList();
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }
}
