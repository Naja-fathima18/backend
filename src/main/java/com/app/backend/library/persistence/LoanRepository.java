package com.app.backend.library.persistence;

import com.app.backend.library.domain.Loan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByBookIdAndReturnedAtIsNull(Long bookId);

    long countByBookId(Long bookId);

    @Modifying
    @Query("delete from Loan l where l.bookId = :bookId")
    int deleteByBookId(@Param("bookId") Long bookId);
}
