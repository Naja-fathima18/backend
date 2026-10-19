package com.app.backend.library.service;

import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.BookAlreadyBorrowedException;
import com.app.backend.library.domain.BookNotBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LendingService {

    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public LendingService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    @Transactional
    public Loan borrow(Long bookId, String memberId) {
        Book book = findBook(bookId);
        book.markBorrowed();
        try {
            bookRepository.saveAndFlush(book);
        } catch (ConcurrencyFailureException ex) {
            log.info("Concurrent borrow of book {} rejected", bookId);
            throw new BookAlreadyBorrowedException(bookId);
        }
        Loan loan = loanRepository.save(Loan.open(bookId, memberId, now()));
        log.info("Opened loan {} for book {}", loan.getId(), bookId);
        return loan;
    }

    @Transactional
    public Loan returnBook(Long bookId) {
        Book book = findBook(bookId);
        Loan loan = loanRepository
                .findByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new BookNotBorrowedException(bookId));
        book.markReturned();
        loan.close(now());
        try {
            bookRepository.saveAndFlush(book);
        } catch (ConcurrencyFailureException ex) {
            log.info("Concurrent return of book {} rejected", bookId);
            throw new BookNotBorrowedException(bookId);
        }
        log.info("Closed loan {} for book {}", loan.getId(), bookId);
        return loan;
    }

    private Book findBook(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
