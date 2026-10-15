package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String ISBN = "9780306406157";

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, loanRepository, CLOCK);
    }

    @Test
    void createsBookWithNormalizedIsbn() {
        when(bookRepository.existsByIsbn(ISBN)).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.create(new BookDetails("Title", "Author", "978-0-306-40615-7", 2020));

        assertThat(created.getIsbn()).isEqualTo(ISBN);
        assertThat(created.getTitle()).isEqualTo("Title");
        assertThat(created.getAuthor()).isEqualTo("Author");
        assertThat(created.getPublishedYear()).isEqualTo(2020);
    }

    @Test
    void acceptsBookPublishedInCurrentYearOfClock() {
        when(bookRepository.saveAndFlush(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.create(new BookDetails("Title", "Author", ISBN, 2026));

        assertThat(created.getPublishedYear()).isEqualTo(2026);
    }

    @Test
    void rejectsCreateWhenIsbnAlreadyExists() {
        when(bookRepository.existsByIsbn(ISBN)).thenReturn(true);
        BookDetails details = new BookDetails("Title", "Author", "978-0306406157", 2020);

        assertThatThrownBy(() -> bookService.create(details))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A book with ISBN 9780306406157 already exists");
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsIsbnWithBadChecksum() {
        BookDetails details = new BookDetails("Title", "Author", "9780306406158", 2020);

        assertThatThrownBy(() -> bookService.create(details))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("isbn"));
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPublishedYearAfterCurrentYearOfClock() {
        BookDetails details = new BookDetails("Title", "Author", ISBN, 2027);

        assertThatThrownBy(() -> bookService.create(details)).isInstanceOfSatisfying(BadRequestException.class, ex -> {
            assertThat(ex.getField()).isEqualTo("publishedYear");
            assertThat(ex.getMessage()).isEqualTo("must not be later than 2026");
        });
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonPositivePublishedYear() {
        BookDetails details = new BookDetails("Title", "Author", ISBN, 0);

        assertThatThrownBy(() -> bookService.create(details))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("publishedYear"));
    }

    @Test
    void returnsBookById() {
        Book book = new Book("Title", "Author", ISBN, 2020);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        Book found = bookService.get(1L);

        assertThat(found).isSameAs(book);
    }

    @Test
    void throwsNotFoundForUnknownBook() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 99 not found");
    }

    @Test
    void updatesBookFields() {
        Book book = new Book("Old", "Old Author", "0306406152", 1999);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbnAndIdNot(ISBN, 1L)).thenReturn(false);
        when(bookRepository.saveAndFlush(book)).thenReturn(book);

        Book updated = bookService.update(1L, new BookDetails("New", "New Author", "978 0306406157", 2021));

        assertThat(updated.getTitle()).isEqualTo("New");
        assertThat(updated.getAuthor()).isEqualTo("New Author");
        assertThat(updated.getIsbn()).isEqualTo(ISBN);
        assertThat(updated.getPublishedYear()).isEqualTo(2021);
    }

    @Test
    void rejectsUpdateWhenIsbnBelongsToAnotherBook() {
        Book book = new Book("Old", "Old Author", "0306406152", 1999);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbnAndIdNot(ISBN, 1L)).thenReturn(true);
        BookDetails details = new BookDetails("New", "New Author", ISBN, 2021);

        assertThatThrownBy(() -> bookService.update(1L, details)).isInstanceOf(ConflictException.class);
        assertThat(book.getIsbn()).isEqualTo("0306406152");
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUpdateWithFuturePublishedYear() {
        BookDetails details = new BookDetails("New", "Author", ISBN, 2030);

        assertThatThrownBy(() -> bookService.update(1L, details))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("publishedYear"));
    }

    @Test
    void throwsNotFoundWhenUpdatingUnknownBook() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        BookDetails details = new BookDetails("New", "Author", ISBN, 2021);

        assertThatThrownBy(() -> bookService.update(99L, details)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchesTitleOrAuthorWithTrimmedQuery() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("title"));
        Page<Book> page = new PageImpl<>(List.of());
        when(bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase("tolkien", "tolkien", pageable))
                .thenReturn(page);

        Page<Book> result = bookService.search("  tolkien ", pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    void listsAllBooksWhenQueryIsBlank() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> page = new PageImpl<>(List.of());
        when(bookRepository.findAll(pageable)).thenReturn(page);

        Page<Book> result = bookService.search(" ", pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    void rejectsSortByUnknownProperty() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> bookService.search(null, pageable))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("sort"));
    }

    @Test
    void deletesBookAndItsLoanHistoryWhenNotBorrowed() {
        Book book = new Book("Title", "Author", ISBN, 2020);
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsOpenLoanForBook(1L)).thenReturn(false);

        bookService.delete(1L);

        InOrder order = inOrder(loanRepository, bookRepository);
        order.verify(loanRepository).deleteAllByBookId(1L);
        order.verify(bookRepository).delete(book);
    }

    @Test
    void rejectsDeletingBorrowedBook() {
        Book book = new Book("Title", "Author", ISBN, 2020);
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsOpenLoanForBook(1L)).thenReturn(true);

        assertThatThrownBy(() -> bookService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Book 1 is currently borrowed and cannot be deleted");
        verify(bookRepository, never()).delete(any());
        verify(loanRepository, never()).deleteAllByBookId(any());
    }

    @Test
    void throwsNotFoundWhenDeletingUnknownBook() {
        when(bookRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.delete(99L)).isInstanceOf(NotFoundException.class);
    }
}
