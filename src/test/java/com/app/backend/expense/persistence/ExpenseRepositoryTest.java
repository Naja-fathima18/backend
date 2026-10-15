package com.app.backend.expense.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpenseRepositoryTest {

    private static final Sort DATE_THEN_ID_DESC = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    @Autowired
    private ExpenseRepository repository;

    @BeforeEach
    void clearTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void sumIncludesFirstAndLastDayAndExcludesDaysJustOutside() {
        save("1.00", ExpenseCategory.FOOD, "2026-09-30");
        save("0.10", ExpenseCategory.FOOD, "2026-10-01");
        save("0.20", ExpenseCategory.FOOD, "2026-10-31");
        save("5.55", ExpenseCategory.TRAVEL, "2026-10-15");
        save("7.00", ExpenseCategory.TRAVEL, "2026-11-01");

        List<CategoryTotal> totals =
                repository.sumByCategoryBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(totals)
                .extracting(CategoryTotal::category, total -> total.total().toPlainString())
                .containsExactlyInAnyOrder(tuple(ExpenseCategory.FOOD, "0.30"), tuple(ExpenseCategory.TRAVEL, "5.55"));
    }

    @Test
    void sumCoversLeapDayInFebruary() {
        save("2.00", ExpenseCategory.BILLS, "2024-02-29");
        save("3.00", ExpenseCategory.BILLS, "2024-03-01");

        List<CategoryTotal> totals =
                repository.sumByCategoryBetween(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));

        assertThat(totals).singleElement().satisfies(total -> {
            assertThat(total.category()).isEqualTo(ExpenseCategory.BILLS);
            assertThat(total.total().toPlainString()).isEqualTo("2.00");
        });
    }

    @Test
    void storesMaximumAmountExactly() {
        Expense saved = save("9999999999.99", ExpenseCategory.OTHER, "2026-10-01");

        Expense reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualByComparingTo("9999999999.99");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void filtersByInclusiveDateRange() {
        save("1.00", ExpenseCategory.FOOD, "2026-09-30");
        Expense first = save("2.00", ExpenseCategory.FOOD, "2026-10-01");
        Expense last = save("3.00", ExpenseCategory.BILLS, "2026-10-31");
        save("4.00", ExpenseCategory.FOOD, "2026-11-01");

        Page<Expense> page = repository.findAll(
                ExpenseSpecifications.matching(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null),
                PageRequest.of(0, 10, DATE_THEN_ID_DESC));

        assertThat(page.getContent()).extracting(Expense::getId).containsExactly(last.getId(), first.getId());
    }

    @Test
    void combinesDateRangeAndCategory() {
        save("2.00", ExpenseCategory.FOOD, "2026-10-01");
        Expense bills = save("3.00", ExpenseCategory.BILLS, "2026-10-02");
        save("4.00", ExpenseCategory.BILLS, "2026-11-02");

        Page<Expense> page = repository.findAll(
                ExpenseSpecifications.matching(
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ExpenseCategory.BILLS),
                PageRequest.of(0, 10, DATE_THEN_ID_DESC));

        assertThat(page.getContent()).extracting(Expense::getId).containsExactly(bills.getId());
    }

    @Test
    void returnsAllWhenNoFilterAndPaginatesWithDateThenIdDescending() {
        Expense older = save("1.00", ExpenseCategory.FOOD, "2026-10-01");
        Expense sameDayFirst = save("2.00", ExpenseCategory.FOOD, "2026-10-02");
        Expense sameDaySecond = save("3.00", ExpenseCategory.TRAVEL, "2026-10-02");

        Page<Expense> firstPage = repository.findAll(
                ExpenseSpecifications.matching(null, null, null), PageRequest.of(0, 2, DATE_THEN_ID_DESC));
        Page<Expense> secondPage = repository.findAll(
                ExpenseSpecifications.matching(null, null, null), PageRequest.of(1, 2, DATE_THEN_ID_DESC));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(Expense::getId)
                .containsExactly(sameDaySecond.getId(), sameDayFirst.getId());
        assertThat(secondPage.getContent()).extracting(Expense::getId).containsExactly(older.getId());
    }

    private Expense save(String amount, ExpenseCategory category, String date) {
        return repository.saveAndFlush(new Expense(new BigDecimal(amount), category, LocalDate.parse(date), null));
    }
}
