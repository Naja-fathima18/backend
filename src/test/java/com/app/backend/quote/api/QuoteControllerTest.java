package com.app.backend.quote.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.quote.domain.Quote;
import com.app.backend.quote.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void returnsRandomQuoteAsTextAndAuthor() throws Exception {
        given(quoteService.randomQuote()).willReturn(new Quote("Stay hungry.", "Steve Jobs"));

        mockMvc.perform(get("/api/quotes/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Stay hungry."))
                .andExpect(jsonPath("$.author").value("Steve Jobs"));
    }
}
