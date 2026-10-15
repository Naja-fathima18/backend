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
import java.time.LocalDate;
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

    static final int MONEY_SCALE = 2;
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Expense create(ExpenseCommand command) {
        Expense expense = repository.save(
                new Expense(normalizeAmount(command.amount()), command.category(), command.date(), command.note()));
        log.info("Created expense {}", expense.getId());
        return expense;
    }

    @Transactional(readOnly = true)
    public Expense get(long id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Expense> list(LocalDate from, LocalDate to, ExpenseCategory category, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from", "must not be after to");
        }
        return repository.findAll(
                ExpenseSpecifications.matching(from, to, category), PageRequest.of(page, size, DEFAULT_SORT));
    }

    @Transactional
    public Expense update(long id, ExpenseCommand command) {
        Expense expense = findOrThrow(id);
        expense.update(normalizeAmount(command.amount()), command.category(), command.date(), command.note());
        repository.flush();
        log.info("Updated expense {}", id);
        return expense;
    }

    @Transactional
    public void delete(long id) {
        repository.delete(findOrThrow(id));
        log.info("Deleted expense {}", id);
    }

    @Transactional(readOnly = true)
    public MonthlySummary summarize(YearMonth month) {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) {
            totals.put(category, zero());
        }
        for (CategoryTotal row : repository.sumByCategoryBetween(month.atDay(1), month.atEndOfMonth())) {
            totals.put(row.category(), toMoney(row.total()));
        }
        BigDecimal overall = totals.values().stream().reduce(zero(), BigDecimal::add);
        return new MonthlySummary(month, Collections.unmodifiableMap(totals), overall);
    }

    private Expense findOrThrow(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Expense " + id + " not found"));
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > MONEY_SCALE) {
            throw new BadRequestException("amount", "must have at most 2 decimal places");
        }
        return stripped.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value == null ? zero() : value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
