package com.app.backend.library.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookTest {

    @Test
    void newBookIsAvailable() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);

        assertThat(book.isAvailable()).isTrue();
    }

    @Test
    void borrowingMakesBookUnavailable() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);

        book.markBorrowed();

        assertThat(book.isAvailable()).isFalse();
    }

    @Test
    void rejectsBorrowingABorrowedBook() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        book.markBorrowed();

        assertThatThrownBy(book::markBorrowed)
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasMessageContaining("already borrowed");
    }

    @Test
    void returningMakesBookAvailableAgain() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        book.markBorrowed();

        book.markReturned();

        assertThat(book.isAvailable()).isTrue();
    }

    @Test
    void rejectsReturningAnAvailableBook() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);

        assertThatThrownBy(book::markReturned).isInstanceOf(BookNotBorrowedException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "' 978-0-441-01359-3 ', 9780441013593",
        "0-441-01359-7, 0441013597",
        "080442957x, 080442957X",
        "9780441013593, 9780441013593"
    })
    void normalizesIsbnByTrimmingAndRemovingHyphens(String raw, String expected) {
        String normalized = Isbn.normalize(raw);

        assertThat(normalized).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780441013593", "978-0-441-01359-3", "0441013597", "0-8044-2957-X", " 0441013597 "})
    void isbnPatternAcceptsIsbn10And13WithOptionalHyphens(String candidate) {
        assertThat(candidate).matches(Isbn.PATTERN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "123",
                "978044101359",
                "97804410135930",
                "-0441013597",
                "0441013597-",
                "04--41013597",
                "abc1234567"
            })
    void isbnPatternRejectsMalformedValues(String candidate) {
        assertThat(candidate).doesNotMatch(Isbn.PATTERN);
    }
}
