package com.app.backend.expense.service;

import com.app.backend.expense.domain.ExpenseCategory;
import java.time.LocalDate;

public record ExpenseFilter(LocalDate from, LocalDate to, ExpenseCategory category) {}
