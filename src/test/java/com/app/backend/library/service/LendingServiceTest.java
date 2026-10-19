package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.library.domain.BookAlreadyBorrowedException;
import com.app.backend.library.domain.BookNotBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LendingServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private LendingService lendingService;

    @Test
    void borrowOpensLoanWhenBookWasAvailable() {
        when(bookRepository.markBorrowedIfAvailable(1L)).thenReturn(1);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = lendingService.borrow(1L, "member-7");

        assertThat(loan.getBookId()).isEqualTo(1L);
        assertThat(loan.getMemberId()).isEqualTo("member-7");
        assertThat(loan.getBorrowedAt()).isNotNull();
        assertThat(loan.getReturnedAt()).isNull();
    }

    @Test
    void borrowRejectsAlreadyBorrowedBook() {
        when(bookRepository.markBorrowedIfAvailable(1L)).thenReturn(0);
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> lendingService.borrow(1L, "member-7"))
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasMessage("Book 1 is already borrowed");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowThrowsNotFoundForUnknownOrConcurrentlyDeletedBook() {
        when(bookRepository.markBorrowedIfAvailable(9L)).thenReturn(0);
        when(bookRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> lendingService.borrow(9L, "member-7")).isInstanceOf(BookNotFoundException.class);
        verify(loanRepository, never()).save(any());
    }

    @Test
    void returnClosesOpenLoanWhenBookWasBorrowed() {
        Loan loan = Loan.open(1L, "member-7", Instant.parse("2026-01-01T10:00:00Z"));
        when(bookRepository.markReturnedIfBorrowed(1L)).thenReturn(1);
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.of(loan));

        Loan closed = lendingService.returnBook(1L);

        assertThat(closed.getReturnedAt()).isNotNull().isAfter(closed.getBorrowedAt());
    }

    @Test
    void returnRejectsBookThatIsNotBorrowed() {
        when(bookRepository.markReturnedIfBorrowed(1L)).thenReturn(0);
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> lendingService.returnBook(1L))
                .isInstanceOf(BookNotBorrowedException.class)
                .hasMessage("Book 1 is not currently borrowed");
        verify(loanRepository, never()).findByBookIdAndReturnedAtIsNull(any());
    }

    @Test
    void returnThrowsNotFoundForUnknownBook() {
        when(bookRepository.markReturnedIfBorrowed(9L)).thenReturn(0);
        when(bookRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> lendingService.returnBook(9L)).isInstanceOf(BookNotFoundException.class);
    }
}
