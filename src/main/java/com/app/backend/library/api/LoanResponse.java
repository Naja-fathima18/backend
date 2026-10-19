package com.app.backend.library.api;

import com.app.backend.library.domain.Loan;
import java.time.Instant;

public record LoanResponse(Long id, Long bookId, String memberId, Instant borrowedAt, Instant returnedAt) {

    static LoanResponse from(Loan loan) {
        return new LoanResponse(
                loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getBorrowedAt(), loan.getReturnedAt());
    }
}
