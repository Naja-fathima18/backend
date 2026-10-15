package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(bookRepository, loanRepository, CLOCK);
    }

    @Test
    void borrowsAvailableBookUnderRowLock() {
        Book book = new Book("Title", "Author", "9780306406157", 2020);
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(1L)).thenReturn(false);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = loanService.borrow(1L, "Alice");

        assertThat(loan.getMemberName()).isEqualTo("Alice");
        assertThat(loan.getBorrowedAt()).isEqualTo(NOW);
        assertThat(loan.isOpen()).isTrue();
        verify(bookRepository, never()).findById(any());
    }

    @Test
    void rejectsBorrowingAlreadyBorrowedBook() {
        Book book = new Book("Title", "Author", "9780306406157", 2020);
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(1L, "Bob"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Book 1 is already borrowed");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundWhenBorrowingUnknownBook() {
        when(bookRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.borrow(99L, "Alice"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 99 not found");
    }

    @Test
    void closesOpenLoanOnReturn() {
        Book book = new Book("Title", "Author", "9780306406157", 2020);
        Loan open = new Loan(book, "Alice", NOW.minusSeconds(3600));
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.of(open));
        when(loanRepository.saveAndFlush(open)).thenReturn(open);

        Loan returned = loanService.returnBook(1L);

        assertThat(returned.getReturnedAt()).isEqualTo(NOW);
        assertThat(returned.isOpen()).isFalse();
    }

    @Test
    void rejectsReturningBookThatIsNotBorrowed() {
        Book book = new Book("Title", "Author", "9780306406157", 2020);
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Book 1 is not borrowed");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundWhenReturningUnknownBook() {
        when(bookRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(99L)).isInstanceOf(NotFoundException.class);
    }
}
