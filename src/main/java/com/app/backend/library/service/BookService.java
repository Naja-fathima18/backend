package com.app.backend.library.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.BookCurrentlyBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.DuplicateIsbnException;
import com.app.backend.library.domain.Isbn;
import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.BookSpecifications;
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

    static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "author", "isbn", "publishedYear");

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public Book create(BookDetails details) {
        String isbn = Isbn.normalize(details.isbn());
        if (bookRepository.existsByIsbn(isbn)) {
            throw new DuplicateIsbnException(isbn);
        }
        Book book = bookRepository.save(new Book(details.title(), details.author(), isbn, details.publishedYear()));
        log.info("Created book {}", book.getId());
        return book;
    }

    @Transactional(readOnly = true)
    public Book get(Long id) {
        return findBook(id);
    }

    @Transactional(readOnly = true)
    public Page<Book> search(String title, String author, Pageable pageable) {
        requireSortableProperties(pageable.getSort());
        return bookRepository.findAll(BookSpecifications.matching(title, author), pageable);
    }

    @Transactional
    public Book update(Long id, BookDetails details) {
        Book book = findBook(id);
        String isbn = Isbn.normalize(details.isbn());
        if (bookRepository.existsByIsbnAndIdNot(isbn, id)) {
            throw new DuplicateIsbnException(isbn);
        }
        book.updateDetails(details.title(), details.author(), isbn, details.publishedYear());
        return bookRepository.saveAndFlush(book);
    }

    @Transactional
    public void delete(Long id) {
        Book book = findBook(id);
        if (!book.isAvailable()) {
            throw new BookCurrentlyBorrowedException(id);
        }
        bookRepository.delete(book);
        log.info("Deleted book {}", id);
    }

    private Book findBook(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    private static void requireSortableProperties(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("sort", "cannot sort by " + order.getProperty());
            }
        }
    }
}
