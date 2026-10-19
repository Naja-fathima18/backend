package com.app.backend.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Loan;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    private Book dune;
    private Book messiah;

    @BeforeEach
    void seedBooks() {
        dune = bookRepository.save(new Book("Dune", "Frank Herbert", "9780441013593", 1965));
        messiah = bookRepository.save(new Book("Dune Messiah", "Frank Herbert", "9780593098233", 1969));
        bookRepository.save(new Book("Foundation", "Isaac Asimov", "9780553293357", 1951));
        bookRepository.save(new Book("100% Pure_Fiction", "Anon", "0441013597", 2000));
        bookRepository.flush();
    }

    @Test
    void searchesTitleCaseInsensitivelyByContains() {
        Page<Book> page = bookRepository.findAll(BookSpecifications.matching("UNE", null), byTitle());

        assertThat(page.getContent()).extracting(Book::getTitle).containsExactly("Dune", "Dune Messiah");
    }

    @Test
    void searchesAuthorCaseInsensitivelyByContains() {
        Page<Book> page = bookRepository.findAll(BookSpecifications.matching(null, "asimov"), byTitle());

        assertThat(page.getContent()).extracting(Book::getTitle).containsExactly("Foundation");
    }

    @Test
    void combinesTitleAndAuthorFilters() {
        Page<Book> page = bookRepository.findAll(BookSpecifications.matching("messiah", "herbert"), byTitle());

        assertThat(page.getContent()).extracting(Book::getTitle).containsExactly("Dune Messiah");
    }

    @Test
    void returnsNothingWhenFiltersDoNotMatchTogether() {
        Page<Book> page = bookRepository.findAll(BookSpecifications.matching("foundation", "herbert"), byTitle());

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void blankFiltersMatchEverything() {
        Page<Book> page = bookRepository.findAll(BookSpecifications.matching(" ", ""), byTitle());

        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void treatsLikeWildcardsLiterally() {
        Page<Book> percent = bookRepository.findAll(BookSpecifications.matching("%", null), byTitle());
        Page<Book> underscore = bookRepository.findAll(BookSpecifications.matching("e_f", null), byTitle());

        assertThat(percent.getContent()).extracting(Book::getTitle).containsExactly("100% Pure_Fiction");
        assertThat(underscore.getContent()).extracting(Book::getTitle).containsExactly("100% Pure_Fiction");
    }

    @Test
    void paginatesResults() {
        Page<Book> page =
                bookRepository.findAll(BookSpecifications.matching(null, null), PageRequest.of(1, 3, Sort.by("title")));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Book::getTitle).containsExactly("Foundation");
    }

    @Test
    void enforcesUniqueIsbn() {
        Book duplicate = new Book("Other", "Other", "9780441013593", 2001);

        assertThatThrownBy(() -> bookRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checksIsbnExistenceExcludingOwnId() {
        assertThat(bookRepository.existsByIsbn("9780441013593")).isTrue();
        assertThat(bookRepository.existsByIsbnAndIdNot("9780441013593", dune.getId()))
                .isFalse();
        assertThat(bookRepository.existsByIsbnAndIdNot("9780441013593", messiah.getId()))
                .isTrue();
    }

    @Test
    void findsOnlyTheOpenLoanOfABook() {
        Loan returned = Loan.open(dune.getId(), "m1", Instant.parse("2026-01-01T10:00:00Z"));
        returned.close(Instant.parse("2026-01-02T10:00:00Z"));
        loanRepository.save(returned);
        Loan open = loanRepository.save(Loan.open(dune.getId(), "m2", Instant.parse("2026-02-01T10:00:00Z")));
        loanRepository.save(Loan.open(messiah.getId(), "m3", Instant.parse("2026-02-01T10:00:00Z")));

        assertThat(loanRepository.findByBookIdAndReturnedAtIsNull(dune.getId()))
                .get()
                .extracting(Loan::getId)
                .isEqualTo(open.getId());
        assertThat(loanRepository.countByBookId(dune.getId())).isEqualTo(2);
    }

    @Test
    void keepsLoanHistoryWhenBookIsDeleted() {
        Loan returned = Loan.open(dune.getId(), "m1", Instant.parse("2026-01-01T10:00:00Z"));
        returned.close(Instant.parse("2026-01-02T10:00:00Z"));
        loanRepository.saveAndFlush(returned);

        bookRepository.delete(dune);
        bookRepository.flush();

        assertThat(bookRepository.findById(dune.getId())).isEmpty();
        assertThat(loanRepository.countByBookId(dune.getId())).isEqualTo(1);
    }

    @Test
    void marksAvailableBookBorrowedOnlyOnce() {
        long versionBefore = dune.getVersion();

        int first = bookRepository.markBorrowedIfAvailable(dune.getId());
        int second = bookRepository.markBorrowedIfAvailable(dune.getId());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        Book reloaded = bookRepository.findById(dune.getId()).orElseThrow();
        assertThat(reloaded.isAvailable()).isFalse();
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void marksBorrowedBookReturnedOnlyOnce() {
        bookRepository.markBorrowedIfAvailable(dune.getId());

        int first = bookRepository.markReturnedIfBorrowed(dune.getId());
        int second = bookRepository.markReturnedIfBorrowed(dune.getId());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(bookRepository.findById(dune.getId()).orElseThrow().isAvailable())
                .isTrue();
    }

    @Test
    void conditionalUpdatesMatchNothingForUnknownBook() {
        assertThat(bookRepository.markBorrowedIfAvailable(-1L)).isZero();
        assertThat(bookRepository.markReturnedIfBorrowed(-1L)).isZero();
    }

    private static PageRequest byTitle() {
        return PageRequest.of(0, 20, Sort.by("title"));
    }
}
