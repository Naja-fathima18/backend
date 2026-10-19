package com.app.backend.ratelimit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.quote.api.QuoteController;
import com.app.backend.quote.service.QuoteService;
import com.app.backend.ratelimit.MutableClock;
import com.app.backend.ratelimit.MutableClockTestConfig;
import com.app.backend.ratelimit.config.RateLimitConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(QuoteController.class)
@Import({QuoteService.class, RateLimitConfig.class, MutableClockTestConfig.class})
@TestPropertySource(properties = {"rate-limit.limit=3", "rate-limit.window=30s"})
class ConfiguredRateLimitWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void appliesLimitAndWindowFromProperties() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestQuote().andExpect(status().isOk());
        }

        ResultActions fourth = requestQuote();

        fourth.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(header().string(RateLimitInterceptor.LIMIT_HEADER, "3"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
        clock.advance(Duration.ofSeconds(30));
        requestQuote().andExpect(status().isOk());
    }

    private ResultActions requestQuote() throws Exception {
        return mockMvc.perform(get("/api/quotes/random").header(RateLimitInterceptor.API_KEY_HEADER, "configured"));
    }
}
