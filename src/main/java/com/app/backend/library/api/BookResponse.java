package com.app.backend.library.api;

import com.app.backend.library.domain.Book;
import java.time.Instant;

public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer publishedYear,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublishedYear(),
                book.getVersion(),
                book.getCreatedAt(),
                book.getUpdatedAt());
    }
}
