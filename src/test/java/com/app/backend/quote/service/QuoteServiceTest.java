package com.app.backend.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.quote.domain.Quote;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    private final QuoteService quoteService = new QuoteService();

    @Test
    void returnsQuoteFromTheCatalogue() {
        Quote quote = quoteService.randomQuote();

        assertThat(quoteService.allQuotes()).contains(quote);
    }

    @Test
    void catalogueQuotesHaveTextAndAuthor() {
        assertThat(quoteService.allQuotes()).isNotEmpty().allSatisfy(quote -> {
            assertThat(quote.text()).isNotBlank();
            assertThat(quote.author()).isNotBlank();
        });
    }
}
