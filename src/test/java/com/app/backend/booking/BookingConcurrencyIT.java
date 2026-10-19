package com.app.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

class BookingConcurrencyIT extends BookingIntegrationTestSupport {

    private static final int PATIENTS = 20;

    @Test
    void onlyOneOfTwentyConcurrentPatientsHoldsTheSameSlot() throws Exception {
        CountDownLatch ready = new CountDownLatch(PATIENTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(PATIENTS);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        try {
            for (int i = 0; i < PATIENTS; i++) {
                String patientId = "patient-" + i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return hold(TEN_AM, patientId).getStatusCode();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(PATIENTS - 1);
            assertThat(availableStartTimes(DAY)).doesNotContain(TEN_AM);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void secondPatientCannotHoldASlotThatIsAlreadyHeld() {
        ResponseEntity<Map> first = hold(TEN_AM, "alice");

        ResponseEntity<Map> second = hold(TEN_AM, "bob");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
