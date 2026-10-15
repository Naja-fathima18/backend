package com.app.backend.library.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.service.BookDetails;
import com.app.backend.library.service.BookService;
import com.app.backend.library.service.LoanService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final String VALID_BODY =
            "{\"title\":\"Dune\",\"author\":\"Frank Herbert\",\"isbn\":\"978-0-306-40615-7\",\"publishedYear\":1965}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private LoanService loanService;

    @Test
    void createsBookAndReturnsLocation() throws Exception {
        when(bookService.create(any())).thenReturn(book(7L));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/books/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.isbn").value("9780306406157"))
                .andExpect(jsonPath("$.title").value("Dune"));

        ArgumentCaptor<BookDetails> captor = ArgumentCaptor.forClass(BookDetails.class);
        verify(bookService).create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new BookDetails("Dune", "Frank Herbert", "978-0-306-40615-7", 1965));
    }

    @Test
    void rejectsBlankTitleAndAuthor() throws Exception {
        String body = "{\"title\":\" \",\"author\":\"\",\"isbn\":\"9780306406157\",\"publishedYear\":1965}";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "author")));

        verifyNoInteractions(bookService);
    }

    @Test
    void rejectsMissingIsbnAndYear() throws Exception {
        String body = "{\"title\":\"Dune\",\"author\":\"Frank Herbert\"}";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("isbn", "publishedYear")));
    }

    @Test
    void rejectsTitleLongerThan255Characters() throws Exception {
        String body = "{\"title\":\"" + "a".repeat(256)
                + "\",\"author\":\"A\",\"isbn\":\"9780306406157\",\"publishedYear\":1965}";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void mapsInvalidIsbnFromServiceToBadRequest() throws Exception {
        when(bookService.create(any()))
                .thenThrow(new BadRequestException("isbn", "must be a valid ISBN-10 or ISBN-13"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("isbn"));
    }

    @Test
    void mapsDuplicateIsbnToConflict() throws Exception {
        when(bookService.create(any()))
                .thenThrow(new ConflictException("A book with ISBN 9780306406157 already exists"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A book with ISBN 9780306406157 already exists"));
    }

    @Test
    void returnsBookById() throws Exception {
        when(bookService.get(7L)).thenReturn(book(7L));

        mockMvc.perform(get("/api/books/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.author").value("Frank Herbert"));
    }

    @Test
    void returnsNotFoundForUnknownBook() throws Exception {
        when(bookService.get(99L)).thenThrow(new NotFoundException("Book 99 not found"));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Book 99 not found"));
    }

    @Test
    void searchesWithQueryAndPagination() throws Exception {
        Page<Book> page = new PageImpl<>(List.of(book(7L)), PageRequest.of(1, 5), 6);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(bookService.search(eq("dune"), pageable.capture())).thenReturn(page);

        mockMvc.perform(get("/api/books")
                        .param("q", "dune")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "title")));
    }

    @Test
    void rejectsSearchQueryLongerThan255Characters() throws Exception {
        mockMvc.perform(get("/api/books").param("q", "a".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void updatesBook() throws Exception {
        when(bookService.update(eq(7L), any())).thenReturn(book(7L));

        mockMvc.perform(put("/api/books/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void rejectsInvalidUpdateBody() throws Exception {
        String body = "{\"title\":\"\",\"author\":\"A\",\"isbn\":\"9780306406157\",\"publishedYear\":-1}";

        mockMvc.perform(put("/api/books/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "publishedYear")));

        verifyNoInteractions(bookService);
    }

    @Test
    void mapsDuplicateIsbnOnUpdateToConflict() throws Exception {
        when(bookService.update(eq(7L), any())).thenThrow(new ConflictException("duplicate"));

        mockMvc.perform(put("/api/books/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesBookWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/books/7")).andExpect(status().isNoContent());

        verify(bookService).delete(7L);
    }

    @Test
    void mapsDeletingBorrowedBookToConflict() throws Exception {
        doThrow(new ConflictException("Book 7 is currently borrowed and cannot be deleted"))
                .when(bookService)
                .delete(7L);

        mockMvc.perform(delete("/api/books/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book 7 is currently borrowed and cannot be deleted"));
    }

    @Test
    void borrowsBookAndReturnsLoan() throws Exception {
        when(loanService.borrow(7L, "Alice")).thenReturn(loan(3L, book(7L), null));

        mockMvc.perform(post("/api/books/7/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.bookId").value(7))
                .andExpect(jsonPath("$.memberName").value("Alice"))
                .andExpect(jsonPath("$.borrowedAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.returnedAt").value(nullValue()));
    }

    @Test
    void rejectsBlankOrTooLongMemberName() throws Exception {
        mockMvc.perform(post("/api/books/7/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memberName"));
        mockMvc.perform(post("/api/books/7/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"" + "m".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memberName"));

        verifyNoInteractions(loanService);
    }

    @Test
    void mapsBorrowingBorrowedBookToConflictProblem() throws Exception {
        when(loanService.borrow(7L, "Bob")).thenThrow(new ConflictException("Book 7 is already borrowed"));

        mockMvc.perform(post("/api/books/7/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"Bob\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Book 7 is already borrowed"));
    }

    @Test
    void mapsBorrowingUnknownBookToNotFound() throws Exception {
        when(loanService.borrow(99L, "Bob")).thenThrow(new NotFoundException("Book 99 not found"));

        mockMvc.perform(post("/api/books/99/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"Bob\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBookAndClosedLoan() throws Exception {
        when(loanService.returnBook(7L)).thenReturn(loan(3L, book(7L), Instant.parse("2026-01-02T10:00:00Z")));

        mockMvc.perform(post("/api/books/7/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.returnedAt").value("2026-01-02T10:00:00Z"));
    }

    @Test
    void mapsReturningUnborrowedBookToConflict() throws Exception {
        when(loanService.returnBook(7L)).thenThrow(new ConflictException("Book 7 is not borrowed"));

        mockMvc.perform(post("/api/books/7/return"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book 7 is not borrowed"));
    }

    private static Book book(Long id) {
        Book book = new Book("Dune", "Frank Herbert", "9780306406157", 1965);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    private static Loan loan(Long id, Book book, Instant returnedAt) {
        Loan loan = new Loan(book, "Alice", Instant.parse("2026-01-01T10:00:00Z"));
        ReflectionTestUtils.setField(loan, "id", id);
        if (returnedAt != null) {
            loan.close(returnedAt);
        }
        return loan;
    }
}
