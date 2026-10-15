package com.app.backend.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.quote.domain.Quote;
import org.junit.jupiter.api.RepeatedTest;

class QuoteServiceTest {

    private final QuoteService quoteService = new QuoteService();

    @RepeatedTest(20)
    void returnsOneOfTheKnownQuotes() {
        Quote quote = quoteService.random();

        assertThat(quoteService.all()).contains(quote);
    }
}
