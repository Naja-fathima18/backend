package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.library.domain.Book;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LendingServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private LendingService lendingService;

    @Test
    void borrowOpensLoanAndMarksBookBorrowed() {
        Book book = availableBook();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = lendingService.borrow(1L, "member-7");

        assertThat(loan.getBookId()).isEqualTo(1L);
        assertThat(loan.getMemberId()).isEqualTo("member-7");
        assertThat(loan.getBorrowedAt()).isNotNull();
        assertThat(loan.getReturnedAt()).isNull();
        assertThat(book.isAvailable()).isFalse();
        verify(bookRepository).saveAndFlush(book);
    }

    @Test
    void borrowRejectsAlreadyBorrowedBook() {
        Book book = availableBook();
        book.markBorrowed();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> lendingService.borrow(1L, "member-7"))
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasMessage("Book 1 is already borrowed");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowTranslatesLostOptimisticLockIntoAlreadyBorrowed() {
        Book book = availableBook();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(book)).thenThrow(new ObjectOptimisticLockingFailureException(Book.class, 1L));

        assertThatThrownBy(() -> lendingService.borrow(1L, "member-7"))
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasMessage("Book 1 is already borrowed");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowThrowsNotFoundForUnknownBook() {
        when(bookRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lendingService.borrow(9L, "member-7")).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void returnClosesOpenLoanAndMakesBookAvailable() {
        Book book = availableBook();
        book.markBorrowed();
        Loan loan = Loan.open(1L, "member-7", Instant.parse("2026-01-01T10:00:00Z"));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.of(loan));

        Loan closed = lendingService.returnBook(1L);

        assertThat(closed.getReturnedAt()).isNotNull().isAfter(closed.getBorrowedAt());
        assertThat(book.isAvailable()).isTrue();
        verify(bookRepository).saveAndFlush(book);
    }

    @Test
    void returnRejectsBookThatIsNotBorrowed() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(availableBook()));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lendingService.returnBook(1L))
                .isInstanceOf(BookNotBorrowedException.class)
                .hasMessage("Book 1 is not currently borrowed");
    }

    @Test
    void returnTranslatesLostOptimisticLockIntoNotBorrowed() {
        Book book = availableBook();
        book.markBorrowed();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(1L))
                .thenReturn(Optional.of(Loan.open(1L, "member-7", Instant.parse("2026-01-01T10:00:00Z"))));
        when(bookRepository.saveAndFlush(book)).thenThrow(new ObjectOptimisticLockingFailureException(Book.class, 1L));

        assertThatThrownBy(() -> lendingService.returnBook(1L)).isInstanceOf(BookNotBorrowedException.class);
    }

    @Test
    void returnThrowsNotFoundForUnknownBook() {
        when(bookRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lendingService.returnBook(9L)).isInstanceOf(BookNotFoundException.class);
    }

    private static Book availableBook() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        ReflectionTestUtils.setField(book, "id", 1L);
        return book;
    }
}
