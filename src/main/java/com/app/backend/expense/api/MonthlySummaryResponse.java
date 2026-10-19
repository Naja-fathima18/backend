package com.app.backend.expense.api;

import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.MonthlySummary;
import java.math.BigDecimal;
import java.util.Map;

public record MonthlySummaryResponse(String month, Map<ExpenseCategory, BigDecimal> totals, BigDecimal total) {

    static MonthlySummaryResponse from(MonthlySummary summary) {
        return new MonthlySummaryResponse(summary.month().toString(), summary.totals(), summary.total());
    }
}
