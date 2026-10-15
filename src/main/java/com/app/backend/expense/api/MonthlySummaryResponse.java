package com.app.backend.expense.api;

import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.MonthlySummary;
import java.util.EnumMap;
import java.util.Map;

public record MonthlySummaryResponse(String month, Map<ExpenseCategory, String> totals, String overallTotal) {

    static MonthlySummaryResponse from(MonthlySummary summary) {
        Map<ExpenseCategory, String> totals = new EnumMap<>(ExpenseCategory.class);
        summary.totals().forEach((category, total) -> totals.put(category, total.toPlainString()));
        return new MonthlySummaryResponse(
                summary.month().toString(), totals, summary.overallTotal().toPlainString());
    }
}
