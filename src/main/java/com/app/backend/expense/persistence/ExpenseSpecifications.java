package com.app.backend.expense.persistence;

import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class ExpenseSpecifications {

    private ExpenseSpecifications() {}

    public static Specification<Expense> matching(LocalDate from, LocalDate to, ExpenseCategory category) {
        return Specification.allOf(onOrAfter(from), onOrBefore(to), inCategory(category));
    }

    private static Specification<Expense> onOrAfter(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("expenseDate"), from);
    }

    private static Specification<Expense> onOrBefore(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("expenseDate"), to);
    }

    private static Specification<Expense> inCategory(ExpenseCategory category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }
}
