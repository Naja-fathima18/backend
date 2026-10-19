package com.app.backend.expense.persistence;

import com.app.backend.expense.domain.ExpenseCategory;
import java.math.BigDecimal;

public record CategoryTotal(ExpenseCategory category, BigDecimal total) {}
