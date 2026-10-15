package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.common.error.ConflictException;
import com.app.backend.library.TestIsbns;
import com.app.backend.library.domain.Book;
import com.app.backend.library.persistence.LoanRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrentBorrowTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private BookService bookService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Test
    void allowsExactlyOneOfManyConcurrentBorrowsOfTheSameBook() throws Exception {
        Book book = bookService.create(new BookDetails("Concurrency", "Tester", TestIsbns.randomIsbn13(), 2001));
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                String member = "member-" + i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return attemptBorrow(book.getId(), member);
                }));
            }
            startGate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get());
        }
        assertThat(outcomes).filteredOn(outcome -> outcome == Outcome.BORROWED).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome == Outcome.CONFLICT).hasSize(CONCURRENT_REQUESTS - 1);
        assertThat(loanRepository.findOpenLoanForBook(book.getId())).isPresent();
        assertThat(loanRepository.findAll().stream()
                        .filter(loan -> loan.getBookId().equals(book.getId())))
                .hasSize(1);
    }

    private Outcome attemptBorrow(Long bookId, String member) {
        try {
            loanService.borrow(bookId, member);
            return Outcome.BORROWED;
        } catch (ConflictException ex) {
            return Outcome.CONFLICT;
        }
    }

    private enum Outcome {
        BORROWED,
        CONFLICT
    }
}
