package com.app.backend.library.persistence;

import com.app.backend.library.domain.Loan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByBookIdAndReturnedAtIsNull(Long bookId);

    long countByBookId(Long bookId);
}
