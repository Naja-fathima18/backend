package com.app.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.config.RateLimitConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "rate-limit.capacity=2",
            "rate-limit.window=30s",
            "rate-limit.header-name=X-Client-Token",
            "rate-limit.path-patterns=/api/quotes/**,/actuator/info"
        })
class RateLimitConfigOverrideIT {

    private static final String QUOTE_PATH = "/api/quotes/random";
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));

    @TestBean(name = RateLimitConfig.CLOCK_BEAN)
    private Clock rateLimitClock;

    @Autowired
    private TestRestTemplate restTemplate;

    static Clock rateLimitClock() {
        return CLOCK;
    }

    @Test
    void appliesConfiguredCapacityWindowAndHeader() {
        ResponseEntity<String> first = getQuote("override-key");
        ResponseEntity<String> second = getQuote("override-key");
        ResponseEntity<String> third = getQuote("override-key");
        CLOCK.advance(Duration.ofSeconds(30));
        ResponseEntity<String> afterWindow = getQuote("override-key");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(afterWindow.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ignoresDefaultHeaderWhenAnotherHeaderIsConfigured() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "default-header-key");

        ResponseEntity<String> response =
                restTemplate.exchange(QUOTE_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectsEveryConfiguredPathPattern() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> getQuote(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Client-Token", apiKey);
        return restTemplate.exchange(QUOTE_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
