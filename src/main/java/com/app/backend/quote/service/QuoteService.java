package com.app.backend.quote.service;

import com.app.backend.quote.domain.Quote;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final List<Quote> QUOTES = List.of(
            new Quote(1, "Simplicity is prerequisite for reliability.", "Edsger W. Dijkstra"),
            new Quote(2, "Premature optimization is the root of all evil.", "Donald Knuth"),
            new Quote(3, "Make it work, make it right, make it fast.", "Kent Beck"),
            new Quote(4, "Programs must be written for people to read.", "Harold Abelson"),
            new Quote(5, "Talk is cheap. Show me the code.", "Linus Torvalds"));

    List<Quote> all() {
        return QUOTES;
    }

    public Quote random() {
        return QUOTES.get(ThreadLocalRandom.current().nextInt(QUOTES.size()));
    }
}
