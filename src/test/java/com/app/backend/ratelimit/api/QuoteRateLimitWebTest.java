package com.app.backend.ratelimit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.quote.api.QuoteController;
import com.app.backend.quote.service.QuoteService;
import com.app.backend.ratelimit.MutableClock;
import com.app.backend.ratelimit.MutableClockTestConfig;
import com.app.backend.ratelimit.config.RateLimitConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(QuoteController.class)
@Import({QuoteService.class, RateLimitConfig.class, MutableClockTestConfig.class})
class QuoteRateLimitWebTest {

    private static final String QUOTE_PATH = "/api/quotes/random";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void allowsTenRequestsInAMinuteAndRejectsTheEleventhWithRetryAfter() throws Exception {
        String apiKey = newApiKey();
        for (int i = 1; i <= 10; i++) {
            requestQuote(apiKey)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").isNotEmpty())
                    .andExpect(jsonPath("$.author").isNotEmpty())
                    .andExpect(header().string(RateLimitInterceptor.LIMIT_HEADER, "10"))
                    .andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, String.valueOf(10 - i)));
        }
        clock.advance(Duration.ofSeconds(15));

        ResultActions eleventh = requestQuote(apiKey);

        eleventh.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded, retry after 45 seconds"))
                .andExpect(jsonPath("$.instance").value(QUOTE_PATH))
                .andExpect(jsonPath("$.retryAfterSeconds").value(45));
    }

    @Test
    void keepsLimitsOfDifferentApiKeysIndependent() throws Exception {
        String exhaustedKey = newApiKey();
        String otherKey = newApiKey();
        for (int i = 0; i < 10; i++) {
            requestQuote(exhaustedKey).andExpect(status().isOk());
        }

        ResultActions otherKeyResult = requestQuote(otherKey);

        otherKeyResult
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "9"));
        requestQuote(exhaustedKey).andExpect(status().isTooManyRequests());
    }

    @Test
    void allowsKeyAgainAfterWindowHasPassed() throws Exception {
        String apiKey = newApiKey();
        for (int i = 0; i < 10; i++) {
            requestQuote(apiKey).andExpect(status().isOk());
        }
        requestQuote(apiKey).andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofMinutes(1));

        ResultActions afterWindow = requestQuote(apiKey);

        afterWindow.andExpect(status().isOk()).andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "9"));
    }

    @Test
    void rejectsRequestWithoutApiKeyAsUnauthorized() throws Exception {
        ResultActions result = mockMvc.perform(get(QUOTE_PATH));

        expectUnauthorized(result);
    }

    @Test
    void rejectsRequestWithBlankApiKeyAsUnauthorized() throws Exception {
        ResultActions result = mockMvc.perform(get(QUOTE_PATH).header(RateLimitInterceptor.API_KEY_HEADER, "   "));

        expectUnauthorized(result);
    }

    private ResultActions requestQuote(String apiKey) throws Exception {
        return mockMvc.perform(get(QUOTE_PATH).header(RateLimitInterceptor.API_KEY_HEADER, apiKey));
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing or blank X-API-Key header"))
                .andExpect(jsonPath("$.instance").value(QUOTE_PATH))
                .andExpect(header().doesNotExist(RateLimitInterceptor.LIMIT_HEADER));
    }

    private static String newApiKey() {
        return "key-" + UUID.randomUUID();
    }
}
