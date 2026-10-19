package com.app.backend.expense.api;

import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(Long id, BigDecimal amount, ExpenseCategory category, LocalDate date, String note) {

    static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getExpenseDate(),
                expense.getNote());
    }
}
