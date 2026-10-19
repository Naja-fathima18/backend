package com.app.backend.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.library.domain.Book;
import com.app.backend.library.domain.BookCurrentlyBorrowedException;
import com.app.backend.library.domain.BookNotFoundException;
import com.app.backend.library.domain.DuplicateIsbnException;
import com.app.backend.library.persistence.BookRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void createStoresNormalizedIsbn() {
        when(bookRepository.existsByIsbn("9780441013593")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.create(new BookDetails("Dune", "Frank Herbert", " 978-0-441-01359-3 ", 1965));

        assertThat(created.getIsbn()).isEqualTo("9780441013593");
        assertThat(created.isAvailable()).isTrue();
    }

    @Test
    void createRejectsDuplicateIsbn() {
        when(bookRepository.existsByIsbn("9780441013593")).thenReturn(true);
        BookDetails details = new BookDetails("Dune", "Frank Herbert", "978-0-441-01359-3", 1965);

        assertThatThrownBy(() -> bookService.create(details))
                .isInstanceOf(DuplicateIsbnException.class)
                .hasMessageContaining("9780441013593");
        verify(bookRepository, never()).save(any());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.get(99L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessage("Book 99 not found");
    }

    @Test
    void updateReplacesAllFields() {
        Book book = new Book("Old", "Someone", "0441013597", 1900);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbnAndIdNot("9780441013593", 1L)).thenReturn(false);
        when(bookRepository.saveAndFlush(book)).thenReturn(book);

        Book updated = bookService.update(1L, new BookDetails("Dune", "Frank Herbert", "978-0441013593", 1965));

        assertThat(updated.getTitle()).isEqualTo("Dune");
        assertThat(updated.getAuthor()).isEqualTo("Frank Herbert");
        assertThat(updated.getIsbn()).isEqualTo("9780441013593");
        assertThat(updated.getPublishedYear()).isEqualTo(1965);
    }

    @Test
    void updateRejectsIsbnOwnedByAnotherBook() {
        Book book = new Book("Old", "Someone", "0441013597", 1900);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbnAndIdNot("9780441013593", 1L)).thenReturn(true);
        BookDetails details = new BookDetails("Dune", "Frank Herbert", "9780441013593", 1965);

        assertThatThrownBy(() -> bookService.update(1L, details)).isInstanceOf(DuplicateIsbnException.class);
        assertThat(book.getIsbn()).isEqualTo("0441013597");
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        when(bookRepository.findById(5L)).thenReturn(Optional.empty());
        BookDetails details = new BookDetails("Dune", "Frank Herbert", "9780441013593", 1965);

        assertThatThrownBy(() -> bookService.update(5L, details)).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void deleteRemovesAvailableBook() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookService.delete(1L);

        verify(bookRepository).delete(book);
    }

    @Test
    void deleteRejectsBorrowedBook() {
        Book book = new Book("Dune", "Frank Herbert", "9780441013593", 1965);
        ReflectionTestUtils.setField(book, "borrowed", true);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> bookService.delete(1L))
                .isInstanceOf(BookCurrentlyBorrowedException.class)
                .hasMessageContaining("currently borrowed");
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        when(bookRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.delete(7L)).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void searchRejectsUnknownSortProperty() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("version"));

        assertThatThrownBy(() -> bookService.search(null, null, pageable))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getField())
                .isEqualTo("sort");
    }

    @Test
    void searchPassesPageableToRepository() {
        PageRequest pageable = PageRequest.of(2, 5, Sort.by("title"));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        bookService.search("dune", null, pageable);

        verify(bookRepository).findAll(ArgumentMatchers.<Specification<Book>>any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(pageable);
    }
}
