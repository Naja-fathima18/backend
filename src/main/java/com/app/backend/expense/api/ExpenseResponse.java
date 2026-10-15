package com.app.backend.expense.api;

import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        String amount,
        ExpenseCategory category,
        LocalDate date,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount().toPlainString(),
                expense.getCategory(),
                expense.getDate(),
                expense.getNote(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
