package com.app.backend.library.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.BookAlreadyBorrowedException;
import com.app.backend.library.domain.BookCurrentlyBorrowedException;
import com.app.backend.library.domain.BookNotBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.DuplicateIsbnException;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.service.BookDetails;
import com.app.backend.library.service.BookService;
import com.app.backend.library.service.LendingService;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final String VALID_BODY = """
            {"title":"Dune","author":"Frank Herbert","isbn":"978-0-441-01359-3","publishedYear":1965}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private LendingService lendingService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(bookService.create(any())).thenReturn(book(1L, false));

        ResultActions result = mockMvc.perform(postJson("/api/books", VALID_BODY));

        result.andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/books/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isbn").value("9780441013593"))
                .andExpect(jsonPath("$.available").value(true));
        verify(bookService).create(new BookDetails("Dune", "Frank Herbert", "978-0-441-01359-3", 1965));
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        ResultActions result = mockMvc.perform(postJson("/api/books", "{}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(
                        jsonPath("$.errors[*].field", containsInAnyOrder("title", "author", "isbn", "publishedYear")));
        verifyNoInteractions(bookService);
    }

    @Test
    void createRejectsPublishedYearInTheFuture() throws Exception {
        int nextYear = Year.now().getValue() + 1;
        String body = """
                {"title":"Dune","author":"Frank Herbert","isbn":"9780441013593","publishedYear":%d}
                """.formatted(nextYear);

        ResultActions result = mockMvc.perform(postJson("/api/books", body));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("publishedYear"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be after the current year"));
    }

    @Test
    void createAcceptsCurrentYear() throws Exception {
        when(bookService.create(any())).thenReturn(book(1L, false));
        String body = """
                {"title":"Dune","author":"Frank Herbert","isbn":"9780441013593","publishedYear":%d}
                """.formatted(Year.now().getValue());

        ResultActions result = mockMvc.perform(postJson("/api/books", body));

        result.andExpect(status().isCreated());
    }

    @Test
    void createRejectsNegativeYearInvalidIsbnAndOverlongTitle() throws Exception {
        String body = """
                {"title":"%s","author":"A","isbn":"12-34","publishedYear":-1}
                """.formatted("x".repeat(256));

        ResultActions result = mockMvc.perform(postJson("/api/books", body));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "isbn", "publishedYear")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must be a valid ISBN-10 or ISBN-13")));
    }

    @Test
    void createReturns409OnDuplicateIsbn() throws Exception {
        when(bookService.create(any())).thenThrow(new DuplicateIsbnException("9780441013593"));

        ResultActions result = mockMvc.perform(postJson("/api/books", VALID_BODY));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("A book with ISBN 9780441013593 already exists"));
    }

    @Test
    void getReturnsBookWithAvailability() throws Exception {
        when(bookService.get(1L)).thenReturn(book(1L, true));

        ResultActions result = mockMvc.perform(get("/api/books/1"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.publishedYear").value(1965))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        when(bookService.get(42L)).thenThrow(new BookNotFoundException(42L));

        ResultActions result = mockMvc.perform(get("/api/books/42"));

        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Book 42 not found"));
    }

    @Test
    void getReturns400ForNonNumericId() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/books/abc"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void listPassesFiltersAndPageable() throws Exception {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by("title"));
        when(bookService.search(eq("dune"), eq("herbert"), any()))
                .thenReturn(new PageImpl<>(List.of(book(3L, false)), pageable, 3));

        ResultActions result = mockMvc.perform(get("/api/books")
                .param("title", "dune")
                .param("author", "herbert")
                .param("page", "1")
                .param("size", "2")
                .param("sort", "title"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookService).search(eq("dune"), eq("herbert"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(pageable);
    }

    @Test
    void listReturns400ForUnknownSortProperty() throws Exception {
        when(bookService.search(any(), any(), any()))
                .thenThrow(new BadRequestException("sort", "cannot sort by version"));

        ResultActions result = mockMvc.perform(get("/api/books").param("sort", "version"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void updateReturnsUpdatedBook() throws Exception {
        when(bookService.update(eq(1L), any())).thenReturn(book(1L, false));

        ResultActions result = mockMvc.perform(putJson("/api/books/1", VALID_BODY));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(bookService).update(1L, new BookDetails("Dune", "Frank Herbert", "978-0-441-01359-3", 1965));
    }

    @Test
    void updateRejectsInvalidBody() throws Exception {
        ResultActions result = mockMvc.perform(putJson("/api/books/1", "{\"title\":\"\"}"));

        result.andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[*].field", containsInAnyOrder("title", "author", "isbn", "publishedYear")));
        verifyNoInteractions(bookService);
    }

    @Test
    void updateReturns409OnDuplicateIsbn() throws Exception {
        when(bookService.update(eq(1L), any())).thenThrow(new DuplicateIsbnException("9780441013593"));

        ResultActions result = mockMvc.perform(putJson("/api/books/1", VALID_BODY));

        result.andExpect(status().isConflict());
    }

    @Test
    void updateReturns404ForUnknownId() throws Exception {
        when(bookService.update(eq(9L), any())).thenThrow(new BookNotFoundException(9L));

        ResultActions result = mockMvc.perform(putJson("/api/books/9", VALID_BODY));

        result.andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        ResultActions result = mockMvc.perform(delete("/api/books/1"));

        result.andExpect(status().isNoContent());
        verify(bookService).delete(1L);
    }

    @Test
    void deleteReturns409WhenBookIsBorrowed() throws Exception {
        doThrow(new BookCurrentlyBorrowedException(1L)).when(bookService).delete(1L);

        ResultActions result = mockMvc.perform(delete("/api/books/1"));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book 1 is currently borrowed and cannot be deleted"));
    }

    @Test
    void deleteReturns404ForUnknownId() throws Exception {
        doThrow(new BookNotFoundException(5L)).when(bookService).delete(5L);

        ResultActions result = mockMvc.perform(delete("/api/books/5"));

        result.andExpect(status().isNotFound());
    }

    @Test
    void borrowReturns201WithLoan() throws Exception {
        Loan loan = Loan.open(1L, "member-7", Instant.parse("2026-03-01T10:00:00Z"));
        ReflectionTestUtils.setField(loan, "id", 11L);
        when(lendingService.borrow(1L, "member-7")).thenReturn(loan);

        ResultActions result = mockMvc.perform(postJson("/api/books/1/borrow", "{\"memberId\":\"member-7\"}"));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.bookId").value(1))
                .andExpect(jsonPath("$.memberId").value("member-7"))
                .andExpect(jsonPath("$.borrowedAt").value("2026-03-01T10:00:00Z"))
                .andExpect(jsonPath("$.returnedAt").doesNotExist());
    }

    @Test
    void borrowRejectsBlankMemberId() throws Exception {
        ResultActions result = mockMvc.perform(postJson("/api/books/1/borrow", "{\"memberId\":\"  \"}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memberId"));
        verifyNoInteractions(lendingService);
    }

    @Test
    void borrowRejectsMissingBody() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/books/1/borrow").contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isBadRequest());
        verifyNoInteractions(lendingService);
    }

    @Test
    void borrowReturns409WhenAlreadyBorrowed() throws Exception {
        when(lendingService.borrow(1L, "member-7")).thenThrow(new BookAlreadyBorrowedException(1L));

        ResultActions result = mockMvc.perform(postJson("/api/books/1/borrow", "{\"memberId\":\"member-7\"}"));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Book 1 is already borrowed"));
    }

    @Test
    void borrowReturns404ForUnknownBook() throws Exception {
        when(lendingService.borrow(9L, "member-7")).thenThrow(new BookNotFoundException(9L));

        ResultActions result = mockMvc.perform(postJson("/api/books/9/borrow", "{\"memberId\":\"member-7\"}"));

        result.andExpect(status().isNotFound());
    }

    @Test
    void returnReturns200WithClosedLoan() throws Exception {
        Loan loan = Loan.open(1L, "member-7", Instant.parse("2026-03-01T10:00:00Z"));
        loan.close(Instant.parse("2026-03-05T09:00:00Z"));
        ReflectionTestUtils.setField(loan, "id", 11L);
        when(lendingService.returnBook(1L)).thenReturn(loan);

        ResultActions result = mockMvc.perform(post("/api/books/1/return"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.returnedAt").value("2026-03-05T09:00:00Z"));
    }

    @Test
    void returnReturns409WhenBookIsNotBorrowed() throws Exception {
        when(lendingService.returnBook(1L)).thenThrow(new BookNotBorrowedException(1L));

        ResultActions result = mockMvc.perform(post("/api/books/1/return"));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book 1 is not currently borrowed"));
    }

    private static Book book(Long id, boolean borrowed) {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        ReflectionTestUtils.setField(book, "id", id);
        ReflectionTestUtils.setField(book, "borrowed", borrowed);
        return book;
    }

    private static MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder putJson(String path, String body) {
        return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
