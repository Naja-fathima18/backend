package com.app.backend.expense.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.persistence.CategoryTotal;
import com.app.backend.expense.persistence.ExpenseRepository;
import com.app.backend.expense.persistence.ExpenseSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Expense.AMOUNT_SCALE);
    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public Expense create(ExpenseDetails details) {
        Expense expense = expenseRepository.save(
                new Expense(details.amount(), details.category(), details.date(), details.note()));
        log.info("Created expense {}", expense.getId());
        return expense;
    }

    @Transactional(readOnly = true)
    public Expense get(long id) {
        return findExisting(id);
    }

    @Transactional(readOnly = true)
    public Page<Expense> list(ExpenseFilter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("from", "must not be after to");
        }
        return expenseRepository.findAll(
                ExpenseSpecifications.matching(filter.from(), filter.to(), filter.category()),
                PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Transactional
    public Expense update(long id, ExpenseDetails details) {
        Expense expense = findExisting(id);
        expense.update(details.amount(), details.category(), details.date(), details.note());
        return expenseRepository.saveAndFlush(expense);
    }

    @Transactional
    public void delete(long id) {
        Expense expense = findExisting(id);
        expenseRepository.delete(expense);
        log.info("Deleted expense {}", id);
    }

    @Transactional(readOnly = true)
    public MonthlySummary summarize(YearMonth month) {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) {
            totals.put(category, ZERO);
        }
        for (CategoryTotal row : expenseRepository.sumByCategoryBetween(month.atDay(1), month.atEndOfMonth())) {
            totals.put(row.category(), toAmountScale(row.total()));
        }
        BigDecimal total = totals.values().stream().reduce(ZERO, BigDecimal::add);
        return new MonthlySummary(month, Collections.unmodifiableMap(totals), total);
    }

    private Expense findExisting(long id) {
        return expenseRepository.findById(id).orElseThrow(() -> new NotFoundException("Expense " + id + " not found"));
    }

    private static BigDecimal toAmountScale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(Expense.AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }
}
