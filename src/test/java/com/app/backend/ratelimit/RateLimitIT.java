package com.app.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.api.RateLimitInterceptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    private static final String QUOTE_PATH = "/api/quotes/random";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void grantsExactlyLimitSuccessesToConcurrentRequestsFromOneKey() throws Exception {
        int requests = 40;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return requestQuote("concurrent-key").getStatusCode();
                }));
            }
            start.countDown();
            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(10);
            assertThat(statuses)
                    .filteredOn(HttpStatus.TOO_MANY_REQUESTS::equals)
                    .hasSize(30);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsQuoteRequestWithoutApiKeyWithProblemDetail() {
        ResponseEntity<String> response = restTemplate.getForEntity(QUOTE_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":401").contains("X-API-Key");
    }

    @Test
    void returnsTooManyRequestsWithRetryAfterOnceLimitIsReached() {
        for (int i = 0; i < 10; i++) {
            assertThat(requestQuote("sequential-key").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> eleventh = requestQuote("sequential-key");

        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(eleventh.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(Long.parseLong(eleventh.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, 60L);
        assertThat(eleventh.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(eleventh.getBody()).contains("\"retryAfterSeconds\"");
    }

    @Test
    void leavesUnprotectedPathsAccessibleWithoutApiKey() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> requestQuote(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RateLimitInterceptor.API_KEY_HEADER, apiKey);
        return restTemplate.exchange(QUOTE_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
