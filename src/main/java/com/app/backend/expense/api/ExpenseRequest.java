package com.app.backend.expense.api;

import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.ExpenseCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record ExpenseRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @NotNull
        @Pattern(regexp = "(?i)(FOOD|TRAVEL|BILLS|OTHER)", message = "must be one of FOOD, TRAVEL, BILLS, OTHER")
        String category,

        @NotNull LocalDate date,
        @Size(max = 500) String note) {

    ExpenseCommand toCommand() {
        return new ExpenseCommand(amount, ExpenseCategory.valueOf(category.toUpperCase(Locale.ROOT)), date, note);
    }
}
