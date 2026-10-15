package com.app.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.config.RateLimitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    private static final String QUOTE_PATH = "/api/quotes/random";
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));

    @TestBean(name = RateLimitConfig.CLOCK_BEAN)
    private Clock rateLimitClock;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static Clock rateLimitClock() {
        return CLOCK;
    }

    @Test
    void allowsTenRequestsPerMinuteAndRejectsTheEleventh() throws Exception {
        String apiKey = uniqueKey();
        List<ResponseEntity<String>> allowed = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allowed.add(getQuote(apiKey));
        }

        ResponseEntity<String> rejected = getQuote(apiKey);

        assertThat(allowed)
                .allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(allowed.get(0).getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(allowed.get(0).getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("9");
        assertThat(allowed.get(9).getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("0");
        assertThat(objectMapper.readTree(allowed.get(0).getBody()).path("text").asText())
                .isNotBlank();
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        long retryAfter = Long.parseLong(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isEqualTo(60L);
        JsonNode body = objectMapper.readTree(rejected.getBody());
        assertThat(body.path("status").asInt()).isEqualTo(429);
        assertThat(body.path("title").asText()).isEqualTo("Too Many Requests");
        assertThat(body.path("retryAfterSeconds").asLong()).isEqualTo(retryAfter);
        assertThat(body.path("instance").asText()).isEqualTo(QUOTE_PATH);
    }

    @Test
    void keepsApiKeysIndependent() {
        String exhaustedKey = uniqueKey();
        String otherKey = uniqueKey();
        for (int i = 0; i < 10; i++) {
            getQuote(exhaustedKey);
        }

        ResponseEntity<String> exhausted = getQuote(exhaustedKey);
        ResponseEntity<String> other = getQuote(otherKey);

        assertThat(exhausted.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(other.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("9");
    }

    @Test
    void allowsClientAgainAfterWindowPasses() {
        String apiKey = uniqueKey();
        for (int i = 0; i < 10; i++) {
            getQuote(apiKey);
        }
        ResponseEntity<String> rejected = getQuote(apiKey);
        CLOCK.advance(Duration.ofMinutes(1));

        ResponseEntity<String> response = getQuote(apiKey);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("9");
    }

    @Test
    void letsExactlyCapacityThroughWhenFiftyRequestsArriveTogether() throws Exception {
        String apiKey = uniqueKey();
        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        List<HttpStatus> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return HttpStatus.valueOf(getQuote(apiKey).getStatusCode().value());
                }));
            }
            start.countDown();
            for (Future<HttpStatus> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(10);
        assertThat(statuses).filteredOn(HttpStatus.TOO_MANY_REQUESTS::equals).hasSize(40);
    }

    @Test
    void rejectsMissingApiKeyWithUnauthorized() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(QUOTE_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asInt()).isEqualTo(401);
        assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.path("detail").asText()).contains("X-API-Key");
        assertThat(body.path("instance").asText()).isEqualTo(QUOTE_PATH);
    }

    @Test
    void rejectsBlankApiKeyWithUnauthorized() {
        ResponseEntity<String> response = getQuote("   ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void doesNotLimitUnprotectedPaths() {
        List<ResponseEntity<String>> responses = new ArrayList<>();

        for (int i = 0; i < 15; i++) {
            responses.add(restTemplate.getForEntity("/actuator/health", String.class));
        }

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().containsKey("X-RateLimit-Limit")).isFalse();
        });
    }

    private ResponseEntity<String> getQuote(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return restTemplate.exchange(QUOTE_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
