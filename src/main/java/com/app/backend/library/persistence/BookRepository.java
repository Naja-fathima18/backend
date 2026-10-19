package com.app.backend.library.persistence;

import com.app.backend.library.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Book b set b.borrowed = true, b.version = b.version + 1 where b.id = :id and b.borrowed = false")
    int markBorrowedIfAvailable(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Book b set b.borrowed = false, b.version = b.version + 1 where b.id = :id and b.borrowed = true")
    int markReturnedIfBorrowed(@Param("id") Long id);
}
