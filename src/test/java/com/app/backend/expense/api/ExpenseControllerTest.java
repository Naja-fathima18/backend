package com.app.backend.expense.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.app.backend.expense.service.ExpenseCommand;
import com.app.backend.expense.service.ExpenseService;
import com.app.backend.expense.service.MonthlySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @Test
    void createsExpenseAndReturnsLocation() throws Exception {
        Expense saved = expense(7L, "12.30", ExpenseCategory.FOOD, OCT_1, "lunch");
        when(expenseService.create(any())).thenReturn(saved);

        ResultActions result =
                postJson("{\"amount\":12.30,\"category\":\"FOOD\",\"date\":\"2026-10-01\",\"note\":\"lunch\"}");

        result.andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/expenses/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.amount").value("12.30"))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.note").value("lunch"));
        verify(expenseService)
                .create(new ExpenseCommand(new BigDecimal("12.30"), ExpenseCategory.FOOD, OCT_1, "lunch"));
    }

    @Test
    void acceptsCategoryCaseInsensitivelyAndAmountAsString() throws Exception {
        when(expenseService.create(any())).thenReturn(expense(1L, "5.00", ExpenseCategory.TRAVEL, OCT_1, null));

        ResultActions result = postJson("{\"amount\":\"5\",\"category\":\"travel\",\"date\":\"2026-10-01\"}");

        result.andExpect(status().isCreated());
        verify(expenseService).create(new ExpenseCommand(new BigDecimal("5"), ExpenseCategory.TRAVEL, OCT_1, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00", "0.00", "10.005", "0.001", "10000000000.00"})
    void rejectsInvalidAmount(String amount) throws Exception {
        ResultActions result = postJson("{\"amount\":" + amount + ",\"category\":\"FOOD\",\"date\":\"2026-10-01\"}");

        expectFieldError(result, "amount");
        verifyNoInteractions(expenseService);
    }

    @Test
    void acceptsMaximumAmount() throws Exception {
        when(expenseService.create(any())).thenReturn(expense(1L, "9999999999.99", ExpenseCategory.BILLS, OCT_1, null));

        ResultActions result = postJson("{\"amount\":9999999999.99,\"category\":\"BILLS\",\"date\":\"2026-10-01\"}");

        result.andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value("9999999999.99"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "amount|{\"category\":\"FOOD\",\"date\":\"2026-10-01\"}",
                "category|{\"amount\":1.00,\"date\":\"2026-10-01\"}",
                "date|{\"amount\":1.00,\"category\":\"FOOD\"}"
            })
    void rejectsMissingRequiredField(String field, String body) throws Exception {
        ResultActions result = postJson(body);

        expectFieldError(result, field)
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        ResultActions result = postJson("{\"amount\":1.00,\"category\":\"FUN\",\"date\":\"2026-10-01\"}");

        expectFieldError(result, "category")
                .andExpect(jsonPath("$.errors[0].message").value("must be one of FOOD, TRAVEL, BILLS, OTHER"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsNoteLongerThan500Characters() throws Exception {
        String note = "x".repeat(501);

        ResultActions result =
                postJson("{\"amount\":1.00,\"category\":\"FOOD\",\"date\":\"2026-10-01\",\"note\":\"" + note + "\"}");

        expectFieldError(result, "note");
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsInvalidDateAsMalformedBody() throws Exception {
        ResultActions result = postJson("{\"amount\":1.00,\"category\":\"FOOD\",\"date\":\"2026-13-01\"}");

        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Malformed request body"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void returnsExpenseById() throws Exception {
        when(expenseService.get(3L)).thenReturn(expense(3L, "0.30", ExpenseCategory.OTHER, OCT_1, null));

        ResultActions result = mockMvc.perform(get("/api/expenses/3"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.amount").value("0.30"))
                .andExpect(jsonPath("$.category").value("OTHER"));
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
        LocalDate date = LocalDate.of(2026, 10, 4);
        when(expenseService.update(eq(4L), any()))
                .thenReturn(expense(4L, "20.00", ExpenseCategory.BILLS, date, "power"));

        ResultActions result = putJson(
                "/api/expenses/4", "{\"amount\":20,\"category\":\"BILLS\",\"date\":\"2026-10-04\",\"note\":\"power\"}");

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("20.00"))
                .andExpect(jsonPath("$.category").value("BILLS"));
        verify(expenseService)
                .update(4L, new ExpenseCommand(new BigDecimal("20"), ExpenseCategory.BILLS, date, "power"));
    }

    @Test
    void updateReturnsNotFoundForUnknownId() throws Exception {
        when(expenseService.update(eq(99L), any())).thenThrow(new NotFoundException("Expense 99 not found"));

        ResultActions result =
                putJson("/api/expenses/99", "{\"amount\":1,\"category\":\"FOOD\",\"date\":\"2026-10-04\"}");

        result.andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsAmountWithMoreThanTwoDecimals() throws Exception {
        ResultActions result =
                putJson("/api/expenses/4", "{\"amount\":1.001,\"category\":\"FOOD\",\"date\":\"2026-10-04\"}");

        expectFieldError(result, "amount");
        verifyNoInteractions(expenseService);
    }

    @Test
    void deletesExpense() throws Exception {
        ResultActions result = mockMvc.perform(delete("/api/expenses/5"));

        result.andExpect(status().isNoContent());
        verify(expenseService).delete(5L);
    }

    @Test
    void deleteReturnsNotFoundForUnknownId() throws Exception {
        doThrow(new NotFoundException("Expense 99 not found"))
                .when(expenseService)
                .delete(99L);

        ResultActions result = mockMvc.perform(delete("/api/expenses/99"));

        result.andExpect(status().isNotFound());
    }

    @Test
    void listsWithFiltersAndPaging() throws Exception {
        LocalDate to = LocalDate.of(2026, 10, 31);
        Expense expense = expense(1L, "1.00", ExpenseCategory.FOOD, OCT_1, null);
        when(expenseService.list(OCT_1, to, ExpenseCategory.FOOD, 1, 5))
                .thenReturn(new PageImpl<>(List.of(expense), PageRequest.of(1, 5), 6));

        ResultActions result = mockMvc.perform(get("/api/expenses")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .param("category", "FOOD")
                .param("page", "1")
                .param("size", "5"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].amount").value("1.00"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listUsesDefaultPagingWithoutFilters() throws Exception {
        when(expenseService.list(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ResultActions result = mockMvc.perform(get("/api/expenses"));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listReturnsBadRequestWhenFromIsAfterTo() throws Exception {
        when(expenseService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BadRequestException("from", "must not be after to"));

        ResultActions result =
                mockMvc.perform(get("/api/expenses").param("from", "2026-10-31").param("to", "2026-10-01"));

        expectFieldError(result, "from");
    }

    @ParameterizedTest
    @CsvSource({"size,0", "size,101", "page,-1", "category,FUN", "from,2026-1-1", "to,yesterday"})
    void listRejectsInvalidQueryParameter(String name, String value) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses").param(name, value));

        expectFieldError(result, name);
        verifyNoInteractions(expenseService);
    }

    @Test
    void returnsMonthlySummaryWithAmountsAsStrings() throws Exception {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        totals.put(ExpenseCategory.FOOD, new BigDecimal("12.30"));
        totals.put(ExpenseCategory.TRAVEL, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.BILLS, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.OTHER, new BigDecimal("0.00"));
        when(expenseService.summarize(YearMonth.of(2026, 10)))
                .thenReturn(new MonthlySummary(YearMonth.of(2026, 10), totals, new BigDecimal("12.30")));

        ResultActions result = mockMvc.perform(get("/api/expenses/summary").param("month", "2026-10"));

        result.andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"month\":\"2026-10\",\"totals\":{\"FOOD\":\"12.30\",\"TRAVEL\":\"0.00\","
                                        + "\"BILLS\":\"0.00\",\"OTHER\":\"0.00\"},\"overallTotal\":\"12.30\"}",
                                true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "october", "2026-10-01"})
    void summaryRejectsInvalidMonth(String month) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses/summary").param("month", month));

        expectFieldError(result, "month");
        verifyNoInteractions(expenseService);
    }

    @Test
    void summaryRejectsMissingMonth() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/expenses/summary"));

        expectFieldError(result, "month")
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
        verifyNoInteractions(expenseService);
    }

    private ResultActions postJson(String body) throws Exception {
        return mockMvc.perform(
                post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(String path, String body) throws Exception {
        return mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static ResultActions expectFieldError(ResultActions result, String field) throws Exception {
        return result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private static Expense expense(Long id, String amount, ExpenseCategory category, LocalDate date, String note) {
        Expense expense = new Expense(new BigDecimal(amount), category, date, note);
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }
}
