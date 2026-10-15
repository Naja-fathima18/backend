package com.app.backend.expense.service;

import com.app.backend.expense.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCommand(BigDecimal amount, ExpenseCategory category, LocalDate date, String note) {}
