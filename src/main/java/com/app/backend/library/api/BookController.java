package com.app.backend.library.api;

import com.app.backend.library.domain.Book;
import com.app.backend.library.service.BookService;
import com.app.backend.library.service.LendingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final LendingService lendingService;

    public BookController(BookService bookService, LendingService lendingService) {
        this.bookService = bookService;
        this.lendingService = lendingService;
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        Book book = bookService.create(request.toDetails());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(book.getId())
                .toUri();
        return ResponseEntity.created(location).body(BookResponse.from(book));
    }

    @GetMapping
    public BookPageResponse list(
            @RequestParam(required = false) @Size(max = 255) String title,
            @RequestParam(required = false) @Size(max = 255) String author,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return BookPageResponse.from(bookService.search(title, author, pageable));
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return BookResponse.from(bookService.get(id));
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return BookResponse.from(bookService.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }

    @PostMapping("/{id}/borrow")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@PathVariable Long id, @Valid @RequestBody BorrowRequest request) {
        return LoanResponse.from(lendingService.borrow(id, request.memberId()));
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnBook(@PathVariable Long id) {
        return LoanResponse.from(lendingService.returnBook(id));
    }
}
