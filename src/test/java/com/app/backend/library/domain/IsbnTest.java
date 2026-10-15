package com.app.backend.library.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IsbnTest {

    @ParameterizedTest
    @CsvSource({
        "978-0-306-40615-7, 9780306406157",
        "978 0 306 40615 7, 9780306406157",
        "9780306406157, 9780306406157",
        "0-306-40615-2, 0306406152",
        "0-8044-2957-x, 080442957X",
        "080442957X, 080442957X"
    })
    void normalizesValidIsbnByStrippingSeparators(String raw, String expected) {
        assertThat(Isbn.normalize(raw)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "9780306406158",
                "0306406153",
                "978030640615",
                "97803064061577",
                "X306406152",
                "978030640615X",
                "978-0-306-40615-a",
                "978_0306406157",
                "   "
            })
    void rejectsMalformedOrBadChecksumIsbn(String raw) {
        assertThat(Isbn.normalize(raw)).isEmpty();
    }
}
