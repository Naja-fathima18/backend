package com.app.backend.library.api;

import com.app.backend.library.domain.Book;
import java.util.List;
import org.springframework.data.domain.Page;

public record BookPageResponse(List<BookResponse> content, int page, int size, long totalElements, int totalPages) {

    static BookPageResponse from(Page<Book> page) {
        return new BookPageResponse(
                page.getContent().stream().map(BookResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
