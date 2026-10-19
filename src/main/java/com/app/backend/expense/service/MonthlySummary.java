package com.app.backend.expense.service;

import com.app.backend.expense.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

public record MonthlySummary(YearMonth month, Map<ExpenseCategory, BigDecimal> totals, BigDecimal total) {}
