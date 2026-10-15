package com.app.backend.library.service;

import com.app.backend.common.error.ConflictException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public LoanService(BookRepository bookRepository, LoanRepository loanRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    @Transactional
    public Loan borrow(Long bookId, String memberName) {
        Book book = lockBook(bookId);
        if (loanRepository.existsByBookIdAndReturnedAtIsNull(bookId)) {
            throw new ConflictException("Book " + bookId + " is already borrowed");
        }
        Loan loan = loanRepository.saveAndFlush(new Loan(book, memberName, Instant.now(clock)));
        log.info("Book {} borrowed under loan {}", bookId, loan.getId());
        return loan;
    }

    @Transactional
    public Loan returnBook(Long bookId) {
        lockBook(bookId);
        Loan loan = loanRepository
                .findByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new ConflictException("Book " + bookId + " is not borrowed"));
        loan.close(Instant.now(clock));
        Loan saved = loanRepository.saveAndFlush(loan);
        log.info("Book {} returned, loan {} closed", bookId, saved.getId());
        return saved;
    }

    private Book lockBook(Long bookId) {
        return bookRepository.findByIdForUpdate(bookId).orElseThrow(() -> BookService.bookNotFound(bookId));
    }
}
