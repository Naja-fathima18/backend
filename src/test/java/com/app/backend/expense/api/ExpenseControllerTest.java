package com.app.backend.expense.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.expense.domain.Expense;
import com.app.backend.expense.domain.ExpenseCategory;
import com.app.backend.expense.service.ExpenseDetails;
import com.app.backend.expense.service.ExpenseFilter;
import com.app.backend.expense.service.ExpenseService;
import com.app.backend.expense.service.MonthlySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    private static final String VALID_BODY =
            "{\"amount\":12.50,\"category\":\"FOOD\",\"date\":\"2024-02-29\",\"note\":\"lunch\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @Test
    void createsExpenseWithLocationHeader() throws Exception {
        when(expenseService.create(any())).thenReturn(expense(7L, "12.50", ExpenseCategory.FOOD, "2024-02-29"));

        ResultActions result = mockMvc.perform(
                post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        result.andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/expenses/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.amount").value(12.50))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.date").value("2024-02-29"));
        verify(expenseService)
                .create(new ExpenseDetails(
                        new BigDecimal("12.50"), ExpenseCategory.FOOD, LocalDate.parse("2024-02-29"), "lunch"));
    }

    @Test
    void acceptsCategoryCaseInsensitively() throws Exception {
        when(expenseService.create(any())).thenReturn(expense(1L, "3.00", ExpenseCategory.TRAVEL, "2024-01-01"));

        ResultActions result = mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":3,\"category\":\"travel\",\"date\":\"2024-01-01\"}"));

        result.andExpect(status().isCreated());
        verify(expenseService)
                .create(new ExpenseDetails(
                        new BigDecimal("3"), ExpenseCategory.TRAVEL, LocalDate.parse("2024-01-01"), null));
    }

    @Test
    void rejectsZeroAmount() throws Exception {
        expectFieldError(body("0", "\"FOOD\"", "\"2024-01-01\""), "amount");
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        expectFieldError(body("-1.00", "\"FOOD\"", "\"2024-01-01\""), "amount");
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() throws Exception {
        expectFieldError(body("1.234", "\"FOOD\"", "\"2024-01-01\""), "amount");
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        expectFieldError("{\"category\":\"FOOD\",\"date\":\"2024-01-01\"}", "amount");
    }

    @Test
    void rejectsMissingCategory() throws Exception {
        expectFieldError("{\"amount\":1.00,\"date\":\"2024-01-01\"}", "category");
    }

    @Test
    void rejectsMissingDate() throws Exception {
        expectFieldError("{\"amount\":1.00,\"category\":\"FOOD\"}", "date");
    }

    @Test
    void rejectsNoteLongerThan500Characters() throws Exception {
        String note = "x".repeat(501);

        expectFieldError(
                "{\"amount\":1.00,\"category\":\"FOOD\",\"date\":\"2024-01-01\",\"note\":\"" + note + "\"}", "note");
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("1.00", "\"SHOPPING\"", "\"2024-01-01\"")));

        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("1.00", "\"FOOD\"", "\"2023-02-29\"")));

        result.andExpect(status().isBadRequest());
        verifyNoInteractions(expenseService);
    }

    @Test
    void returnsExpenseById() throws Exception {
        when(expenseService.get(3L)).thenReturn(expense(3L, "0.30", ExpenseCategory.BILLS, "2024-04-30"));

        ResultActions result = mockMvc.perform(get("/api/expenses/3"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.amount").value(0.30))
                .andExpect(jsonPath("$.category").value("BILLS"));
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        when(expenseService.get(99L)).thenThrow(new NotFoundException("Expense 99 not found"));

        ResultActions result = mockMvc.perform(get("/api/expenses/99"));

        result.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Expense 99 not found"));
    }

    @Test
    void updatesExpense() throws Exception {
        when(expenseService.update(eq(5L), any())).thenReturn(expense(5L, "12.50", ExpenseCategory.FOOD, "2024-02-29"));

        ResultActions result = mockMvc.perform(
                put("/api/expenses/5").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        verify(expenseService)
                .update(
                        5L,
                        new ExpenseDetails(
                                new BigDecimal("12.50"), ExpenseCategory.FOOD, LocalDate.parse("2024-02-29"), "lunch"));
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownId() throws Exception {
        when(expenseService.update(eq(99L), any())).thenThrow(new NotFoundException("Expense 99 not found"));

        ResultActions result = mockMvc.perform(
                put("/api/expenses/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        result.andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUpdateBody() throws Exception {
        ResultActions result = mockMvc.perform(put("/api/expenses/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("1.001", "\"FOOD\"", "\"2024-01-01\"")));

        result.andExpect(status().isBadRequest());
        verifyNoInteractions(expenseService);
    }

    @Test
    void deletesExpense() throws Exception {
        ResultActions result = mockMvc.perform(delete("/api/expenses/4"));

        result.andExpect(status().isNoContent());
        verify(expenseService).delete(4L);
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownId() throws Exception {
        doThrow(new NotFoundException("Expense 99 not found"))
                .when(expenseService)
                .delete(99L);

        ResultActions result = mockMvc.perform(delete("/api/expenses/99"));

        result.andExpect(status().isNotFound());
    }

    @Test
    void listsExpensesWithFiltersAndPaging() throws Exception {
        ExpenseFilter filter =
                new ExpenseFilter(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-03-31"), ExpenseCategory.FOOD);
        when(expenseService.list(filter, 1, 2))
                .thenReturn(new PageImpl<>(
                        List.of(expense(9L, "1.00", ExpenseCategory.FOOD, "2024-03-02")), PageRequest.of(1, 2), 3));

        ResultActions result = mockMvc.perform(get("/api/expenses")
                .param("from", "2024-03-01")
                .param("to", "2024-03-31")
                .param("category", "food")
                .param("page", "1")
                .param("size", "2"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listsWithoutFiltersUsingDefaultPage() throws Exception {
        when(expenseService.list(new ExpenseFilter(null, null, null), 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ResultActions result = mockMvc.perform(get("/api/expenses"));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void returnsBadRequestWhenFromIsAfterTo() throws Exception {
        when(expenseService.list(any(), anyInt(), anyInt()))
                .thenThrow(new BadRequestException("from", "must not be after to"));

        ResultActions result =
                mockMvc.perform(get("/api/expenses").param("from", "2024-03-02").param("to", "2024-03-01"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be after to"));
    }

    @Test
    void rejectsUnknownCategoryFilter() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses").param("category", "SHOPPING"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsInvalidDateFilter() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses").param("from", "03/01/2024"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsPageSizeAboveLimit() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses").param("size", "101"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void returnsMonthlySummaryWithEveryCategory() throws Exception {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        totals.put(ExpenseCategory.FOOD, new BigDecimal("0.30"));
        totals.put(ExpenseCategory.TRAVEL, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.BILLS, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.OTHER, new BigDecimal("0.00"));
        when(expenseService.summarize(YearMonth.of(2024, 2)))
                .thenReturn(new MonthlySummary(YearMonth.of(2024, 2), totals, new BigDecimal("0.30")));

        ResultActions result = mockMvc.perform(get("/api/expenses/summary").param("month", "2024-02"));

        result.andExpect(status().isOk())
                .andExpect(content()
                        .json("{\"month\":\"2024-02\",\"total\":0.30,"
                                + "\"totals\":{\"FOOD\":0.30,\"TRAVEL\":0.00,\"BILLS\":0.00,\"OTHER\":0.00}}"))
                .andExpect(content().string(containsString("\"total\":0.30")));
    }

    @Test
    void rejectsMissingSummaryMonth() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses/summary"));

        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("month")));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsInvalidSummaryMonth() throws Exception {
        for (String month : List.of("2024-13", "2024-2", "2024", "abc", "2024-02-01")) {
            ResultActions result = mockMvc.perform(get("/api/expenses/summary").param("month", month));

            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("month"));
        }
        verifyNoInteractions(expenseService);
    }

    private void expectFieldError(String json, String field) throws Exception {
        ResultActions result = mockMvc.perform(
                post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json));

        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        verifyNoInteractions(expenseService);
    }

    private static String body(String amount, String category, String date) {
        return "{\"amount\":" + amount + ",\"category\":" + category + ",\"date\":" + date + "}";
    }

    private static Expense expense(long id, String amount, ExpenseCategory category, String date) {
        Expense expense = new Expense(new BigDecimal(amount), category, LocalDate.parse(date), null);
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }
}
