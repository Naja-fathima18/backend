package com.app.backend.library.api;

import com.app.backend.library.service.BookDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author,
        @NotBlank @Size(max = 32) String isbn,
        @NotNull @Positive Integer publishedYear) {

    BookDetails toDetails() {
        return new BookDetails(title, author, isbn, publishedYear);
    }
}
