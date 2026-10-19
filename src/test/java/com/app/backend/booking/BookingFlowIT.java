package com.app.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.booking.service.BookingExpiryService;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BookingFlowIT extends BookingIntegrationTestSupport {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    @Autowired
    private BookingExpiryService expiryService;

    @Test
    void holdReturnsCreatedBookingWithLocationAndHoldExpiry() {
        ResponseEntity<Map> response = hold(TEN_AM, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = response.getBody();
        assertThat(body.get("doctorId")).isEqualTo(1);
        assertThat(body.get("startTime")).isEqualTo(TEN_AM);
        assertThat(body.get("endTime")).isEqualTo("2030-01-07T10:30:00Z");
        assertThat(body.get("patientId")).isEqualTo("alice");
        assertThat(body.get("status")).isEqualTo("HELD");
        assertThat(body.get("holdExpiresAt")).isEqualTo("2030-01-07T08:05:00Z");
        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/api/bookings/" + body.get("id"));
        assertThat(getBooking(body.get("id")).getBody()).isEqualTo(body);
    }

    @Test
    void listsAllWorkingHourSlotsWhenNothingIsTaken() {
        List<String> slots = availableStartTimes(DAY);

        assertThat(slots).hasSize(16);
        assertThat(slots.getFirst()).isEqualTo("2030-01-07T09:00:00Z");
        assertThat(slots.getLast()).isEqualTo("2030-01-07T16:30:00Z");
    }

    @Test
    void slotListingExcludesHeldConfirmedAndPastSlots() {
        clock.setInstant(Instant.parse("2030-01-07T09:10:00Z"));
        assertThat(hold(TEN_AM, "alice").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object confirmedId = hold("2030-01-07T11:00:00Z", "bob").getBody().get("id");
        assertThat(confirm(confirmedId, "bob").getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> slots = availableStartTimes(DAY);

        assertThat(slots)
                .hasSize(13)
                .doesNotContain("2030-01-07T09:00:00Z", TEN_AM, "2030-01-07T11:00:00Z")
                .contains("2030-01-07T09:30:00Z", "2030-01-07T10:30:00Z", "2030-01-07T16:30:00Z");
    }

    @Test
    void expiredHoldMakesSlotAvailableAndBookableByAnotherPatient() {
        Object aliceBooking = hold(TEN_AM, "alice").getBody().get("id");
        assertThat(availableStartTimes(DAY)).doesNotContain(TEN_AM);

        clock.advance(HOLD_DURATION);

        assertThat(availableStartTimes(DAY)).contains(TEN_AM);
        assertThat(getBooking(aliceBooking).getBody().get("status")).isEqualTo("EXPIRED");
        ResponseEntity<Map> bob = hold(TEN_AM, "bob");
        assertThat(bob.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(confirm(aliceBooking, "alice").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(confirm(bob.getBody().get("id"), "bob").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void holdIsStillActiveJustBeforeExpiry() {
        hold(TEN_AM, "alice");

        clock.advance(HOLD_DURATION.minusSeconds(1));

        assertThat(availableStartTimes(DAY)).doesNotContain(TEN_AM);
        assertThat(hold(TEN_AM, "bob").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void holderConfirmsBeforeExpiry() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        clock.advance(HOLD_DURATION.minusSeconds(1));

        ResponseEntity<Map> response = confirm(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CONFIRMED");
        clock.advance(Duration.ofHours(1));
        assertThat(getBooking(bookingId).getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(hold("2030-01-07T10:00:00Z", "bob").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmAfterExpiryIsRejectedAndFreesTheSlot() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        clock.advance(HOLD_DURATION);

        ResponseEntity<Map> response = confirm(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        clock.setInstant(NOW);
        assertThat(getBooking(bookingId).getBody().get("status")).isEqualTo("EXPIRED");
        assertThat(availableStartTimes(DAY)).contains(TEN_AM);
        assertThat(confirm(bookingId, "alice").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmByAnotherPatientIsForbidden() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");

        ResponseEntity<Map> response = confirm(bookingId, "mallory");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getBooking(bookingId).getBody().get("status")).isEqualTo("HELD");
    }

    @Test
    void confirmingTwiceIsAConflict() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        confirm(bookingId, "alice");

        ResponseEntity<Map> response = confirm(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingConfirmedBookingFreesTheSlot() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        confirm(bookingId, "alice");
        assertThat(availableStartTimes(DAY)).doesNotContain(TEN_AM);

        ResponseEntity<Map> response = cancel(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(availableStartTimes(DAY)).contains(TEN_AM);
        assertThat(hold(TEN_AM, "bob").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cancellingAHeldBookingIsAConflict() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");

        ResponseEntity<Map> response = cancel(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(availableStartTimes(DAY)).doesNotContain(TEN_AM);
    }

    @Test
    void cancellingByAnotherPatientIsForbidden() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        confirm(bookingId, "alice");

        ResponseEntity<Map> response = cancel(bookingId, "mallory");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getBooking(bookingId).getBody().get("status")).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellingTwiceIsAConflict() {
        Object bookingId = hold(TEN_AM, "alice").getBody().get("id");
        confirm(bookingId, "alice");
        cancel(bookingId, "alice");

        ResponseEntity<Map> response = cancel(bookingId, "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsPastMisalignedAndOutOfHoursStartTimes() {
        assertThat(hold("2030-01-07T07:30:00Z", "alice").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hold("2030-01-07T10:15:00Z", "alice").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hold("2030-01-07T10:00:01Z", "alice").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hold("2030-01-07T08:30:00Z", "alice").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hold("2030-01-07T17:00:00Z", "alice").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hold("2030-01-07T16:30:00Z", "alice").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void acceptsStartTimeExpressedWithAnOffset() {
        ResponseEntity<Map> response = hold("2030-01-07T15:30:00+05:30", "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("startTime")).isEqualTo(TEN_AM);
    }

    @Test
    void unknownDoctorAndBookingReturnNotFound() {
        ResponseEntity<Map> unknownDoctorHold = restTemplate.postForEntity(
                "/api/bookings", Map.of("doctorId", 999, "startTime", TEN_AM, "patientId", "alice"), Map.class);
        ResponseEntity<Map> unknownDoctorSlots =
                restTemplate.getForEntity("/api/doctors/999/slots?date=" + DAY, Map.class);

        assertThat(unknownDoctorHold.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownDoctorSlots.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getBooking(999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(confirm(999, "alice").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listsSeededDoctors() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/doctors", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("content")).hasSize(3);
        assertThat(response.getBody().get("totalElements")).isEqualTo(3);
    }

    @Test
    void sweepMarksExpiredHoldsAndReleasesTheirSlots() {
        Object expiredId = hold(TEN_AM, "alice").getBody().get("id");
        clock.advance(Duration.ofMinutes(3));
        Object activeId = hold("2030-01-07T11:00:00Z", "bob").getBody().get("id");
        clock.advance(Duration.ofMinutes(2));

        int expired = expiryService.expireStaleHolds();

        assertThat(expired).isEqualTo(1);
        clock.setInstant(NOW);
        assertThat(getBooking(expiredId).getBody().get("status")).isEqualTo("EXPIRED");
        assertThat(getBooking(activeId).getBody().get("status")).isEqualTo("HELD");
        assertThat(availableStartTimes(DAY)).contains(TEN_AM).doesNotContain("2030-01-07T11:00:00Z");
    }
}
