package com.app.backend.quote.api;

import com.app.backend.quote.domain.Quote;

public record QuoteResponse(long id, String text, String author) {

    static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.id(), quote.text(), quote.author());
    }
}
