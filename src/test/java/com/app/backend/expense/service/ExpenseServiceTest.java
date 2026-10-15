package com.app.backend.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Mock
    private ExpenseRepository repository;

    @InjectMocks
    private ExpenseService service;

    @Test
    void summaryListsEveryCategoryWithZeroWhenNoExpenses() {
        YearMonth month = YearMonth.of(2026, 10);
        when(repository.sumByCategoryBetween(OCT_1, LocalDate.of(2026, 10, 31))).thenReturn(List.of());

        MonthlySummary summary = service.summarize(month);

        assertThat(summary.month()).isEqualTo(month);
        assertThat(summary.totals()).containsOnlyKeys(ExpenseCategory.values());
        assertThat(summary.totals().values())
                .allSatisfy(total -> assertThat(total.toPlainString()).isEqualTo("0.00"));
        assertThat(summary.overallTotal().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void summaryAddsCategoryTotalsExactlyWithScaleTwo() {
        YearMonth month = YearMonth.of(2026, 10);
        when(repository.sumByCategoryBetween(OCT_1, LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(
                        new CategoryTotal(ExpenseCategory.FOOD, new BigDecimal("0.10")),
                        new CategoryTotal(ExpenseCategory.BILLS, new BigDecimal("0.2"))));

        MonthlySummary summary = service.summarize(month);

        assertThat(summary.totals().get(ExpenseCategory.FOOD).toPlainString()).isEqualTo("0.10");
        assertThat(summary.totals().get(ExpenseCategory.BILLS).toPlainString()).isEqualTo("0.20");
        assertThat(summary.totals().get(ExpenseCategory.TRAVEL).toPlainString()).isEqualTo("0.00");
        assertThat(summary.totals().get(ExpenseCategory.OTHER).toPlainString()).isEqualTo("0.00");
        assertThat(summary.overallTotal().toPlainString()).isEqualTo("0.30");
    }

    @Test
    void summaryKeepsCategoriesInDeclarationOrder() {
        when(repository.sumByCategoryBetween(any(), any())).thenReturn(List.of());

        MonthlySummary summary = service.summarize(YearMonth.of(2026, 10));

        assertThat(summary.totals().keySet())
                .containsExactly(
                        ExpenseCategory.FOOD, ExpenseCategory.TRAVEL, ExpenseCategory.BILLS, ExpenseCategory.OTHER);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10, 2026-10-01, 2026-10-31",
        "2024-02, 2024-02-01, 2024-02-29",
        "2026-02, 2026-02-01, 2026-02-28",
        "2000-02, 2000-02-01, 2000-02-29",
        "2100-02, 2100-02-01, 2100-02-28",
        "2026-12, 2026-12-01, 2026-12-31"
    })
    void summaryQueriesFromFirstToLastDayOfMonthInclusive(String month, LocalDate start, LocalDate end) {
        when(repository.sumByCategoryBetween(start, end)).thenReturn(List.of());

        service.summarize(YearMonth.parse(month));

        verify(repository).sumByCategoryBetween(start, end);
    }

    @Test
    void createStoresAmountWithScaleTwo() {
        when(repository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Expense created = service.create(new ExpenseCommand(new BigDecimal("5"), ExpenseCategory.FOOD, OCT_1, "x"));

        assertThat(created.getAmount().toPlainString()).isEqualTo("5.00");
        assertThat(created.getCategory()).isEqualTo(ExpenseCategory.FOOD);
        assertThat(created.getDate()).isEqualTo(OCT_1);
        assertThat(created.getNote()).isEqualTo("x");
    }

    @Test
    void createAcceptsTrailingZerosBeyondTwoDecimals() {
        when(repository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Expense created =
                service.create(new ExpenseCommand(new BigDecimal("12.300"), ExpenseCategory.FOOD, OCT_1, null));

        assertThat(created.getAmount().toPlainString()).isEqualTo("12.30");
    }

    @Test
    void createRejectsMoreThanTwoDecimalsInsteadOfRounding() {
        ExpenseCommand command = new ExpenseCommand(new BigDecimal("10.005"), ExpenseCategory.FOOD, OCT_1, null);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must have at most 2 decimal places");
        verify(repository, never()).save(any());
    }

    @Test
    void updateReplacesAllFields() {
        Expense existing = new Expense(new BigDecimal("1.00"), ExpenseCategory.FOOD, OCT_1, "old");
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        LocalDate newDate = LocalDate.of(2026, 10, 5);

        Expense updated =
                service.update(4L, new ExpenseCommand(new BigDecimal("2.5"), ExpenseCategory.OTHER, newDate, null));

        assertThat(updated.getAmount().toPlainString()).isEqualTo("2.50");
        assertThat(updated.getCategory()).isEqualTo(ExpenseCategory.OTHER);
        assertThat(updated.getDate()).isEqualTo(newDate);
        assertThat(updated.getNote()).isNull();
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ExpenseCommand command = new ExpenseCommand(BigDecimal.ONE, ExpenseCategory.FOOD, OCT_1, null);

        assertThatThrownBy(() -> service.update(99L, command))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Expense 99 not found");
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesExistingExpense() {
        Expense existing = new Expense(BigDecimal.ONE, ExpenseCategory.FOOD, OCT_1, null);
        when(repository.findById(4L)).thenReturn(Optional.of(existing));

        service.delete(4L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any(Expense.class));
    }

    @Test
    void listRejectsFromAfterTo() {
        LocalDate from = LocalDate.of(2026, 10, 2);

        assertThatThrownBy(() -> service.list(from, OCT_1, null, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .satisfies(
                        ex -> assertThat(((BadRequestException) ex).getField()).isEqualTo("from"));
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSortsByDateThenIdDescending() {
        Page<Expense> empty = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.list(OCT_1, OCT_1, ExpenseCategory.FOOD, 2, 10);

        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(ExpenseService.DEFAULT_SORT);
        assertThat(ExpenseService.DEFAULT_SORT.toString()).isEqualTo("date: DESC,id: DESC");
    }
}
