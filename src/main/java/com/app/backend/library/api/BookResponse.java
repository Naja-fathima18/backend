package com.app.backend.library.api;

import com.app.backend.library.domain.Book;

public record BookResponse(Long id, String title, String author, String isbn, int publishedYear, boolean available) {

    static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublishedYear(),
                book.isAvailable());
    }
}
