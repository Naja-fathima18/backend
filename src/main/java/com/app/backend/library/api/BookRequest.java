package com.app.backend.library.api;

import com.app.backend.library.domain.Isbn;
import com.app.backend.library.service.BookDetails;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author,

        @NotBlank @Pattern(regexp = Isbn.PATTERN, message = "must be a valid ISBN-10 or ISBN-13")
        String isbn,

        @NotNull @Min(0) @NotAfterCurrentYear Integer publishedYear) {

    BookDetails toDetails() {
        return new BookDetails(title, author, isbn, publishedYear);
    }
}
