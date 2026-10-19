package com.app.backend.expense.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum ExpenseCategory {
    FOOD,
    TRAVEL,
    BILLS,
    OTHER;

    @JsonCreator
    public static ExpenseCategory fromValue(String value) {
        String candidate = value == null ? "" : value.trim();
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown expense category"));
    }
}
