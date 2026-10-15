package com.app.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.booking.api.AvailableSlotsResponse;
import com.app.backend.booking.config.BookingConfig;
import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.booking.service.BookingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "booking.expiry-sweep-enabled=false")
class BookingFlowIT {

    private static final long DOCTOR_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2032, 6, 1);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final int PATIENTS = 20;

    record BookingBody(Long bookingId, String status, Instant expiresAt) {}

    @Autowired
    private TestRestTemplate rest;

    @TestBean(name = BookingConfig.CLOCK, methodName = "com.app.backend.booking.BookingTestClock#bookingClock")
    private Clock bookingClock;

    private final MutableClock clock = BookingTestClock.CLOCK;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void reset() {
        clock.set(BookingTestClock.START);
        bookingRepository.deleteAllInBatch();
    }

    private ResponseEntity<BookingBody> hold(LocalTime startTime, String patientName) {
        Map<String, Object> body = Map.of(
                "doctorId",
                DOCTOR_ID,
                "date",
                DATE.toString(),
                "startTime",
                startTime.toString(),
                "patientName",
                patientName);
        return rest.postForEntity("/api/bookings/holds", body, BookingBody.class);
    }

    private ResponseEntity<BookingBody> confirm(Long bookingId) {
        return rest.postForEntity("/api/bookings/{id}/confirm", null, BookingBody.class, bookingId);
    }

    private ResponseEntity<BookingBody> cancel(Long bookingId) {
        return rest.postForEntity("/api/bookings/{id}/cancel", null, BookingBody.class, bookingId);
    }

    private List<LocalTime> availableStartTimes() {
        ResponseEntity<AvailableSlotsResponse> response = rest.getForEntity(
                "/api/doctors/{doctorId}/slots?date={date}", AvailableSlotsResponse.class, DOCTOR_ID, DATE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().availableStartTimes();
    }

    @Test
    void availableSlotsListTheGridMinusHeldAndConfirmedSlots() {
        hold(NINE, "Ann Held");
        Long confirmedId = hold(LocalTime.of(10, 30), "Bob Confirmed").getBody().bookingId();
        confirm(confirmedId);

        List<LocalTime> available = availableStartTimes();

        assertThat(available).hasSize(14).doesNotContain(NINE, LocalTime.of(10, 30));
        assertThat(available).contains(LocalTime.of(9, 30), LocalTime.of(16, 30));
    }

    @Test
    void holdThenConfirmThenConfirmAgain() {
        ResponseEntity<BookingBody> held = hold(NINE, "Ann Patient");

        ResponseEntity<BookingBody> confirmed = confirm(held.getBody().bookingId());
        ResponseEntity<BookingBody> confirmedAgain = confirm(held.getBody().bookingId());

        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(held.getHeaders().getLocation())
                .hasPath("/api/bookings/" + held.getBody().bookingId());
        assertThat(held.getBody().status()).isEqualTo("HELD");
        assertThat(held.getBody().expiresAt()).isEqualTo(BookingTestClock.START.plus(Duration.ofMinutes(5)));
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(confirmedAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void holdOnTakenSlotIsConflict() {
        hold(NINE, "Ann Patient");

        ResponseEntity<BookingBody> second = hold(NINE, "Bob Patient");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void holdOnUnknownDoctorIsNotFound() {
        Map<String, Object> body =
                Map.of("doctorId", 999, "date", DATE.toString(), "startTime", "09:00", "patientName", "Ann");

        ResponseEntity<String> response = rest.postForEntity("/api/bookings/holds", body, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void slotsOfUnknownDoctorAreNotFound() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/doctors/{id}/slots?date={date}", String.class, 999, DATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredHoldFreesTheSlotAndCannotBeConfirmed() {
        Long expiredHoldId = hold(NINE, "Ann Patient").getBody().bookingId();
        assertThat(availableStartTimes()).doesNotContain(NINE);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(availableStartTimes()).contains(NINE);
        ResponseEntity<BookingBody> newHold = hold(NINE, "Bob Patient");
        assertThat(newHold.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(confirm(expiredHoldId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bookingRepository.findById(expiredHoldId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(confirm(newHold.getBody().bookingId()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmingExpiredHoldPersistsExpiryAndReleasesTheSlot() {
        Long holdId = hold(NINE, "Ann Patient").getBody().bookingId();
        clock.advance(Duration.ofMinutes(6));

        ResponseEntity<BookingBody> response = confirm(holdId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Booking stored = bookingRepository.findById(holdId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(stored.getActiveSlotKey()).isNull();
    }

    @Test
    void expirySweepMarksOverdueHoldsExpired() {
        Long overdueId = hold(NINE, "Ann Patient").getBody().bookingId();
        clock.advance(Duration.ofMinutes(3));
        Long liveId = hold(LocalTime.of(9, 30), "Bob Patient").getBody().bookingId();
        clock.advance(Duration.ofMinutes(2));

        int expired = bookingService.expireOverdueHolds();

        assertThat(expired).isEqualTo(1);
        assertThat(bookingRepository.findById(overdueId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingRepository.findById(liveId).orElseThrow().getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void cancellingConfirmedBookingFreesTheSlot() {
        Long bookingId = hold(NINE, "Ann Patient").getBody().bookingId();
        confirm(bookingId);
        assertThat(availableStartTimes()).doesNotContain(NINE);

        ResponseEntity<BookingBody> cancelled = cancel(bookingId);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo("CANCELLED");
        assertThat(availableStartTimes()).contains(NINE);
        assertThat(hold(NINE, "Bob Patient").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cancel(bookingId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingHeldBookingIsConflict() {
        Long bookingId = hold(NINE, "Ann Patient").getBody().bookingId();

        assertThat(cancel(bookingId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(availableStartTimes()).doesNotContain(NINE);
    }

    @Test
    void unknownBookingIsNotFound() {
        assertThat(confirm(987654L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(cancel(987654L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @RepeatedTest(5)
    void twentyPatientsRacingForOneSlotYieldExactlyOneHold(RepetitionInfo repetition) throws Exception {
        LocalTime slot = NINE.plusMinutes(30L * repetition.getCurrentRepetition());
        CountDownLatch ready = new CountDownLatch(PATIENTS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService patients = Executors.newFixedThreadPool(PATIENTS);
        List<Future<HttpStatusCode>> attempts = new ArrayList<>();
        try {
            for (int patient = 0; patient < PATIENTS; patient++) {
                String name = "Patient " + patient;
                Callable<HttpStatusCode> attempt = () -> {
                    ready.countDown();
                    go.await();
                    return hold(slot, name).getStatusCode();
                };
                attempts.add(patients.submit(attempt));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            go.countDown();

            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> attempt : attempts) {
                statuses.add(attempt.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(PATIENTS - 1);
            assertThat(bookingRepository.countByActiveSlotKey(Booking.slotKey(DOCTOR_ID, DATE, slot)))
                    .isEqualTo(1);
        } finally {
            patients.shutdownNow();
        }
    }
}
