package com.app.backend.expense.api;

import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.ExpenseFilter;
import com.app.backend.expense.service.ExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        Expense expense = expenseService.create(request.toDetails());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(expense.getId())
                .toUri();
        return ResponseEntity.created(location).body(ExpenseResponse.from(expense));
    }

    @GetMapping
    public ExpensePageResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ExpensePageResponse.from(expenseService.list(new ExpenseFilter(from, to, category), page, size));
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse summary(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return MonthlySummaryResponse.from(expenseService.summarize(month));
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable @Positive long id) {
        return ExpenseResponse.from(expenseService.get(id));
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable @Positive long id, @Valid @RequestBody ExpenseRequest request) {
        return ExpenseResponse.from(expenseService.update(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
