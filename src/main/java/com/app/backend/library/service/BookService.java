package com.app.backend.library.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.Isbn;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import java.time.Clock;
import java.time.Year;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "title", "author", "isbn", "publishedYear", "createdAt", "updatedAt");

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    @Transactional
    public Book create(BookDetails details) {
        String isbn = normalizedIsbn(details.isbn());
        int publishedYear = validPublishedYear(details.publishedYear());
        if (bookRepository.existsByIsbn(isbn)) {
            throw duplicateIsbn(isbn);
        }
        Book book = bookRepository.saveAndFlush(new Book(details.title(), details.author(), isbn, publishedYear));
        log.info("Created book {}", book.getId());
        return book;
    }

    @Transactional(readOnly = true)
    public Book get(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> bookNotFound(id));
    }

    @Transactional(readOnly = true)
    public Page<Book> search(String query, Pageable pageable) {
        requireSortableProperties(pageable.getSort());
        if (query == null || query.isBlank()) {
            return bookRepository.findAll(pageable);
        }
        String term = query.strip();
        return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(term, term, pageable);
    }

    @Transactional
    public Book update(Long id, BookDetails details) {
        String isbn = normalizedIsbn(details.isbn());
        int publishedYear = validPublishedYear(details.publishedYear());
        Book book = bookRepository.findById(id).orElseThrow(() -> bookNotFound(id));
        if (bookRepository.existsByIsbnAndIdNot(isbn, id)) {
            throw duplicateIsbn(isbn);
        }
        book.update(details.title(), details.author(), isbn, publishedYear);
        Book saved = bookRepository.saveAndFlush(book);
        log.info("Updated book {}", id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Book book = bookRepository.findByIdForUpdate(id).orElseThrow(() -> bookNotFound(id));
        if (loanRepository.existsOpenLoanForBook(id)) {
            throw new ConflictException("Book " + id + " is currently borrowed and cannot be deleted");
        }
        loanRepository.deleteAllByBookId(id);
        bookRepository.delete(book);
        log.info("Deleted book {}", id);
    }

    static NotFoundException bookNotFound(Long id) {
        return new NotFoundException("Book " + id + " not found");
    }

    private static ConflictException duplicateIsbn(String isbn) {
        return new ConflictException("A book with ISBN " + isbn + " already exists");
    }

    private static String normalizedIsbn(String rawIsbn) {
        return Isbn.normalize(rawIsbn)
                .orElseThrow(() -> new BadRequestException("isbn", "must be a valid ISBN-10 or ISBN-13"));
    }

    private int validPublishedYear(Integer publishedYear) {
        if (publishedYear == null || publishedYear < 1) {
            throw new BadRequestException("publishedYear", "must be greater than 0");
        }
        int currentYear = Year.now(clock).getValue();
        if (publishedYear > currentYear) {
            throw new BadRequestException("publishedYear", "must not be later than " + currentYear);
        }
        return publishedYear;
    }

    private static void requireSortableProperties(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("sort", "cannot sort by " + order.getProperty());
            }
        }
    }
}
