package com.app.backend.library.api;

import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.service.BookService;
import com.app.backend.library.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final LoanService loanService;

    public BookController(BookService bookService, LoanService loanService) {
        this.bookService = bookService;
        this.loanService = loanService;
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
    public PageResponse<BookResponse> search(
            @RequestParam(required = false) @Size(max = 255) String q,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(bookService.search(q, pageable), BookResponse::from);
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/borrow")
    public ResponseEntity<LoanResponse> borrow(@PathVariable Long id, @Valid @RequestBody BorrowRequest request) {
        Loan loan = loanService.borrow(id, request.memberName());
        return ResponseEntity.status(201).body(LoanResponse.from(loan));
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnBook(@PathVariable Long id) {
        return LoanResponse.from(loanService.returnBook(id));
    }
}
