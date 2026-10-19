package com.app.backend.expense.api;

import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.ExpenseDetails;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @NotNull ExpenseCategory category,
        @NotNull LocalDate date,
        @Size(max = 500) String note) {

    ExpenseDetails toDetails() {
        return new ExpenseDetails(amount, category, date, note);
    }
}
