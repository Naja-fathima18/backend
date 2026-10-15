package com.app.backend.library.persistence;

import com.app.backend.library.domain.Loan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Query("select count(l) > 0 from Loan l where l.book.id = :bookId and l.returnedAt is null")
    boolean existsOpenLoanForBook(@Param("bookId") Long bookId);

    @Query("select l from Loan l where l.book.id = :bookId and l.returnedAt is null")
    Optional<Loan> findOpenLoanForBook(@Param("bookId") Long bookId);

    @Modifying
    @Query("delete from Loan l where l.book.id = :bookId")
    int deleteAllByBookId(@Param("bookId") Long bookId);
}
