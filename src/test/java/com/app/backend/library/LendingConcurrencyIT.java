package com.app.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.BookAlreadyBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.Loan;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import com.app.backend.library.service.BookDetails;
import com.app.backend.library.service.BookService;
import com.app.backend.library.service.LendingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
class LendingConcurrencyIT {

    private static final int CONCURRENT_BORROWERS = 8;

    @Autowired
    private LendingService lendingService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @AfterEach
    void cleanUp() {
        loanRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
    }

    @RepeatedTest(5)
    void concurrentBorrowsOfOneBookCreateExactlyOneLoan() throws Exception {
        Long bookId = bookRepository
                .save(new Book("Dune", "Frank Herbert", "9780441013593", 1965))
                .getId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BORROWERS);
        List<Future<Outcome>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_BORROWERS; i++) {
                String memberId = "member-" + i;
                futures.add(executor.submit(() -> attemptBorrow(start, bookId, memberId)));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(Outcome.BORROWED::equals).hasSize(1);
            assertThat(outcomes).filteredOn(Outcome.ALREADY_BORROWED::equals).hasSize(CONCURRENT_BORROWERS - 1);
            assertThat(loanRepository.countByBookId(bookId)).isEqualTo(1);
            assertThat(bookRepository.findById(bookId))
                    .get()
                    .extracting(Book::isAvailable)
                    .isEqualTo(false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void borrowWinsOverStaleTitleEditWhichIsRejectedAsConflictingUpdate() {
        Long bookId = bookRepository
                .save(new Book("Dune", "Frank Herbert", "9780441013593", 1965))
                .getId();
        Book staleEdit = bookRepository.findById(bookId).orElseThrow();

        Loan loan = lendingService.borrow(bookId, "member-1");
        staleEdit.updateDetails("Dune (revised)", "Frank Herbert", "9780441013593", 1965);

        assertThat(loan.getBookId()).isEqualTo(bookId);
        assertThatThrownBy(() -> bookRepository.saveAndFlush(staleEdit))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        Book stored = bookRepository.findById(bookId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Dune");
        assertThat(stored.isAvailable()).isFalse();
    }

    @Test
    void borrowAfterTitleEditStillSucceeds() {
        Long bookId = bookRepository
                .save(new Book("Dune", "Frank Herbert", "9780441013593", 1965))
                .getId();
        bookService.update(bookId, new BookDetails("Dune (revised)", "Frank Herbert", "9780441013593", 1965));

        Loan loan = lendingService.borrow(bookId, "member-1");

        assertThat(loan.getBookId()).isEqualTo(bookId);
        assertThat(loanRepository.countByBookId(bookId)).isEqualTo(1);
    }

    @Test
    void borrowOfDeletedBookIsNotFound() {
        Long bookId = bookRepository
                .save(new Book("Dune", "Frank Herbert", "9780441013593", 1965))
                .getId();
        bookService.delete(bookId);

        assertThatThrownBy(() -> lendingService.borrow(bookId, "member-1")).isInstanceOf(BookNotFoundException.class);
    }

    private Outcome attemptBorrow(CountDownLatch start, Long bookId, String memberId) throws InterruptedException {
        start.await();
        try {
            lendingService.borrow(bookId, memberId);
            return Outcome.BORROWED;
        } catch (BookAlreadyBorrowedException ex) {
            return Outcome.ALREADY_BORROWED;
        }
    }

    private enum Outcome {
        BORROWED,
        ALREADY_BORROWED
    }
}
