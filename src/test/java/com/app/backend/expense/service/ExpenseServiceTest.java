package com.app.backend.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.persistence.CategoryTotal;
import com.app.backend.expense.persistence.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void summarizesFromFirstToLastDayOfLeapFebruary() {
        when(expenseRepository.sumByCategoryBetween(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-29")))
                .thenReturn(List.of(new CategoryTotal(ExpenseCategory.FOOD, new BigDecimal("0.3"))));

        MonthlySummary summary = expenseService.summarize(YearMonth.of(2024, 2));

        assertThat(summary.month()).isEqualTo(YearMonth.of(2024, 2));
        assertThat(summary.totals().get(ExpenseCategory.FOOD)).isEqualTo(new BigDecimal("0.30"));
        assertThat(summary.total()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void fillsMissingCategoriesWithZeroAtScaleTwo() {
        when(expenseRepository.sumByCategoryBetween(any(), any()))
                .thenReturn(List.of(new CategoryTotal(ExpenseCategory.TRAVEL, new BigDecimal("10.10"))));

        MonthlySummary summary = expenseService.summarize(YearMonth.of(2023, 11));

        assertThat(summary.totals())
                .containsExactly(
                        entry(ExpenseCategory.FOOD, new BigDecimal("0.00")),
                        entry(ExpenseCategory.TRAVEL, new BigDecimal("10.10")),
                        entry(ExpenseCategory.BILLS, new BigDecimal("0.00")),
                        entry(ExpenseCategory.OTHER, new BigDecimal("0.00")));
        assertThat(summary.total()).isEqualTo(new BigDecimal("10.10"));
    }

    @Test
    void addsCategoryTotalsExactly() {
        when(expenseRepository.sumByCategoryBetween(any(), any()))
                .thenReturn(List.of(
                        new CategoryTotal(ExpenseCategory.FOOD, new BigDecimal("0.10")),
                        new CategoryTotal(ExpenseCategory.BILLS, new BigDecimal("0.20"))));

        MonthlySummary summary = expenseService.summarize(YearMonth.of(2024, 1));

        assertThat(summary.total()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void storesAmountAtScaleTwo() {
        when(expenseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExpenseDetails details =
                new ExpenseDetails(new BigDecimal("5"), ExpenseCategory.OTHER, LocalDate.parse("2024-01-01"), "x");

        Expense created = expenseService.create(details);

        assertThat(created.getAmount()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void throwsNotFoundForUnknownId() {
        when(expenseRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Expense 42 not found");
    }

    @Test
    void updatesExistingExpense() {
        Expense existing =
                new Expense(new BigDecimal("1.00"), ExpenseCategory.FOOD, LocalDate.parse("2024-01-01"), null);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(expenseRepository.saveAndFlush(existing)).thenReturn(existing);
        ExpenseDetails details =
                new ExpenseDetails(new BigDecimal("2.5"), ExpenseCategory.BILLS, LocalDate.parse("2024-02-02"), "gas");

        Expense updated = expenseService.update(1L, details);

        assertThat(updated.getAmount()).isEqualTo(new BigDecimal("2.50"));
        assertThat(updated.getCategory()).isEqualTo(ExpenseCategory.BILLS);
        assertThat(updated.getExpenseDate()).isEqualTo(LocalDate.parse("2024-02-02"));
        assertThat(updated.getNote()).isEqualTo("gas");
    }

    @Test
    void doesNotDeleteUnknownExpense() {
        when(expenseRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.delete(9L)).isInstanceOf(NotFoundException.class);
        verify(expenseRepository, never()).delete(any(Expense.class));
    }

    @Test
    void deletesExistingExpense() {
        Expense existing =
                new Expense(new BigDecimal("1.00"), ExpenseCategory.FOOD, LocalDate.parse("2024-01-01"), null);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(existing));

        expenseService.delete(1L);

        verify(expenseRepository).delete(existing);
    }

    @Test
    void rejectsFromAfterToWithoutQuerying() {
        ExpenseFilter filter = new ExpenseFilter(LocalDate.parse("2024-02-02"), LocalDate.parse("2024-02-01"), null);

        assertThatThrownBy(() -> expenseService.list(filter, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must not be after to");
        verifyNoInteractions(expenseRepository);
    }
}
