package com.app.backend.expense.persistence;

import com.app.backend.expense.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Query("""
            select new com.app.backend.expense.persistence.CategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.expenseDate between :start and :end
            group by e.category
            """)
    List<CategoryTotal> sumByCategoryBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
