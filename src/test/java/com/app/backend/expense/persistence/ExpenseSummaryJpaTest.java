package com.app.backend.expense.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.ExpenseFilter;
import com.app.backend.expense.service.ExpenseService;
import com.app.backend.expense.service.MonthlySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ExpenseService.class)
class ExpenseSummaryJpaTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseService expenseService;

    @Test
    void countsFirstAndLastDayOfMonthAndExcludesAdjacentMonths() {
        save("1.00", ExpenseCategory.FOOD, "2024-02-29");
        save("2.00", ExpenseCategory.FOOD, "2024-01-31");
        save("4.00", ExpenseCategory.FOOD, "2024-02-01");
        save("8.00", ExpenseCategory.FOOD, "2024-03-01");
        save("16.00", ExpenseCategory.TRAVEL, "2024-03-31");
        save("32.00", ExpenseCategory.TRAVEL, "2024-04-01");

        MonthlySummary february = expenseService.summarize(YearMonth.of(2024, 2));
        MonthlySummary march = expenseService.summarize(YearMonth.of(2024, 3));

        assertThat(february.totals().get(ExpenseCategory.FOOD)).isEqualTo(new BigDecimal("5.00"));
        assertThat(february.total()).isEqualTo(new BigDecimal("5.00"));
        assertThat(march.totals().get(ExpenseCategory.FOOD)).isEqualTo(new BigDecimal("8.00"));
        assertThat(march.totals().get(ExpenseCategory.TRAVEL)).isEqualTo(new BigDecimal("16.00"));
        assertThat(march.total()).isEqualTo(new BigDecimal("24.00"));
    }

    @Test
    void sumsDecimalAmountsExactly() {
        save("0.10", ExpenseCategory.BILLS, "2024-05-10");
        save("0.20", ExpenseCategory.BILLS, "2024-05-11");
        save("0.05", ExpenseCategory.OTHER, "2024-05-12");

        MonthlySummary summary = expenseService.summarize(YearMonth.of(2024, 5));

        assertThat(summary.totals().get(ExpenseCategory.BILLS)).isEqualTo(new BigDecimal("0.30"));
        assertThat(summary.totals().get(ExpenseCategory.OTHER)).isEqualTo(new BigDecimal("0.05"));
        assertThat(summary.total()).isEqualTo(new BigDecimal("0.35"));
    }

    @Test
    void reportsEveryCategoryWithZeroWhenMonthHasNoExpenses() {
        save("9.99", ExpenseCategory.FOOD, "2024-06-30");

        MonthlySummary summary = expenseService.summarize(YearMonth.of(2024, 7));

        assertThat(summary.totals()).containsOnlyKeys(ExpenseCategory.values());
        assertThat(summary.totals().values())
                .allSatisfy(value -> assertThat(value).isEqualTo(new BigDecimal("0.00")));
        assertThat(summary.total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void filtersByInclusiveDateRangeAndCategoryNewestFirst() {
        Expense before = save("1.00", ExpenseCategory.FOOD, "2024-03-09");
        Expense onFrom = save("2.00", ExpenseCategory.FOOD, "2024-03-10");
        Expense otherCategory = save("3.00", ExpenseCategory.TRAVEL, "2024-03-15");
        Expense onTo = save("4.00", ExpenseCategory.FOOD, "2024-03-20");
        Expense sameDayLater = save("5.00", ExpenseCategory.FOOD, "2024-03-20");
        Expense after = save("6.00", ExpenseCategory.FOOD, "2024-03-21");
        ExpenseFilter filter =
                new ExpenseFilter(LocalDate.parse("2024-03-10"), LocalDate.parse("2024-03-20"), ExpenseCategory.FOOD);

        Page<Expense> page = expenseService.list(filter, 0, 20);

        assertThat(page.getContent())
                .extracting(Expense::getId)
                .containsExactly(sameDayLater.getId(), onTo.getId(), onFrom.getId())
                .doesNotContain(before.getId(), otherCategory.getId(), after.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void paginatesFilteredResults() {
        save("1.00", ExpenseCategory.OTHER, "2024-08-01");
        save("2.00", ExpenseCategory.OTHER, "2024-08-02");
        save("3.00", ExpenseCategory.OTHER, "2024-08-03");

        Page<Expense> secondPage = expenseService.list(new ExpenseFilter(null, null, null), 1, 2);

        assertThat(secondPage.getContent()).extracting(Expense::getAmount).containsExactly(new BigDecimal("1.00"));
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void acceptsSingleDayRange() {
        save("7.00", ExpenseCategory.FOOD, "2024-09-05");
        LocalDate day = LocalDate.parse("2024-09-05");

        Page<Expense> page = expenseService.list(new ExpenseFilter(day, day, null), 0, 20);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void rejectsRangeWhereFromIsAfterTo() {
        ExpenseFilter filter = new ExpenseFilter(LocalDate.parse("2024-03-21"), LocalDate.parse("2024-03-20"), null);

        assertThatThrownBy(() -> expenseService.list(filter, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must not be after to");
    }

    private Expense save(String amount, ExpenseCategory category, String date) {
        return expenseRepository.saveAndFlush(
                new Expense(new BigDecimal(amount), category, LocalDate.parse(date), null));
    }
}
