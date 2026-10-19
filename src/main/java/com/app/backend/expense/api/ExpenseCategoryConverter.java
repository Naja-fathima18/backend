package com.app.backend.expense.api;

import com.app.backend.expense.domain.ExpenseCategory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCategoryConverter implements Converter<String, ExpenseCategory> {

    @Override
    public ExpenseCategory convert(String source) {
        return source.isBlank() ? null : ExpenseCategory.fromValue(source);
    }
}
