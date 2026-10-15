package com.app.backend.expense.api;

import com.app.backend.expense.domain.Expense;
import java.util.List;
import org.springframework.data.domain.Page;

public record ExpensePageResponse(
        List<ExpenseResponse> content, int page, int size, long totalElements, int totalPages) {

    static ExpensePageResponse from(Page<Expense> page) {
        return new ExpensePageResponse(
                page.getContent().stream().map(ExpenseResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
