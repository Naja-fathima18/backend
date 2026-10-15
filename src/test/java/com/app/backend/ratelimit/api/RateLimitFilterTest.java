package com.app.backend.ratelimit.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.ratelimit.MutableClock;
import com.app.backend.ratelimit.config.RateLimitProperties;
import com.app.backend.ratelimit.service.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static final String HEADER = "X-API-Key";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RateLimitProperties properties =
            new RateLimitProperties(2, Duration.ofMinutes(1), HEADER, List.of("/api/quotes/**"));
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final RateLimitFilter filter =
            new RateLimitFilter(new RateLimiter(properties, clock), properties, objectMapper);

    @ParameterizedTest
    @ValueSource(strings = {"/api/books", "/api/expenses/1", "/api/files", "/api/bookings", "/actuator/health", "/api"})
    void passesUnprotectedPathsThroughWithoutApiKey(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(path, null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isNull();
    }

    @Test
    void matchesProtectedPathsWithinApplicationContextPath() throws Exception {
        MockHttpServletRequest request = request("/app/api/quotes/random", null);
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsMissingApiKeyWithProblemDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/quotes/random", null), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.path("detail").asText()).isEqualTo("Missing API key: send it in the X-API-Key header");
        assertThat(body.path("instance").asText()).isEqualTo("/api/quotes/random");
    }

    @Test
    void passesAllowedRequestWithRateLimitHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/quotes/random", "key-a"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isEqualTo("2");
        assertThat(response.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("1");
    }

    @Test
    void rejectsRequestOverLimitWithRetryAfter() throws Exception {
        filter.doFilter(request("/api/quotes/random", "key-a"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("/api/quotes/random", "key-a"), new MockHttpServletResponse(), new MockFilterChain());
        clock.advance(Duration.ofSeconds(15));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/quotes/random", "key-a"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("45");
        assertThat(response.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("0");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("status").asInt()).isEqualTo(429);
        assertThat(body.path("retryAfterSeconds").asLong()).isEqualTo(45);
        assertThat(body.path("detail").asText()).isEqualTo("Rate limit exceeded: retry after 45 seconds");
    }

    private static MockHttpServletRequest request(String uri, String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (apiKey != null) {
            request.addHeader(HEADER, apiKey);
        }
        return request;
    }
}
