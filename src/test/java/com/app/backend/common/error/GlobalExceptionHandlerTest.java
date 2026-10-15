package com.app.backend.common.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ErrorProbeController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsFieldErrorsWhenRequestBodyIsInvalid() throws Exception {
        ResultActions result = mockMvc.perform(
                post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"quantity\":0}"));

        expectProblem(result, 400, "Bad Request", "Validation failed", "/probe/body")
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")))
                .andExpect(jsonPath(
                        "$.errors[*].message",
                        containsInAnyOrder("must not be blank", "must be greater than or equal to 1")));
    }

    @Test
    void acceptsValidRequestBody() throws Exception {
        ResultActions result = mockMvc.perform(post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ok\",\"quantity\":2}"));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ok"));
    }

    @Test
    void returnsFieldErrorsWhenMethodParameterIsInvalid() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/params").param("size", "0"));

        expectProblem(result, 400, "Bad Request", "Validation failed", "/probe/params")
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 1"));
    }

    @Test
    void returnsFieldErrorsWhenConstraintViolationIsThrown() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/constraint-violation"));

        expectProblem(result, 400, "Bad Request", "Validation failed", "/probe/constraint-violation")
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")));
    }

    @Test
    void returnsFieldErrorWhenRequiredParameterIsMissing() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/required"));

        expectProblem(result, 400, "Bad Request", "Validation failed", "/probe/required")
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
    }

    @Test
    void returnsFieldErrorWithoutEchoingValueWhenArgumentTypeMismatches() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/items/not-a-number"));

        expectProblem(result, 400, "Bad Request", "Validation failed", "/probe/items/not-a-number")
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"))
                .andExpect(jsonPath("$.detail", not(containsString("not-a-number"))));
    }

    @Test
    void returnsBadRequestWhenBodyIsMalformed() throws Exception {
        ResultActions result = mockMvc.perform(
                post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"));

        expectProblem(result, 400, "Bad Request", "Malformed request body", "/probe/body")
                .andExpect(jsonPath("$.errors", empty()));
    }

    @Test
    void returnsPayloadTooLargeWhenUploadExceedsLimit() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/upload-too-large"));

        expectProblem(
                result,
                413,
                "Payload Too Large",
                "Uploaded file exceeds the maximum allowed size",
                "/probe/upload-too-large");
    }

    @Test
    void mapsNotFoundExceptionToNotFound() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/not-found"));

        expectProblem(result, 404, "Not Found", "Book 42 not found", "/probe/not-found")
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void mapsConflictExceptionToConflict() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/conflict"));

        expectProblem(result, 409, "Conflict", "Slot already booked", "/probe/conflict");
    }

    @Test
    void mapsBadRequestExceptionToBadRequestWithEmptyErrors() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/bad-request"));

        expectProblem(result, 400, "Bad Request", "Range is invalid", "/probe/bad-request")
                .andExpect(jsonPath("$.errors", empty()));
    }

    @Test
    void mapsBadRequestExceptionWithFieldToFieldError() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/bad-request-field"));

        expectProblem(result, 400, "Bad Request", "must be after startDate", "/probe/bad-request-field")
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("endDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must be after startDate"));
    }

    @Test
    void mapsOptimisticLockFailureToGenericConflict() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/optimistic-lock"));

        expectProblem(result, 409, "Conflict", "Conflicting update", "/probe/optimistic-lock")
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void mapsDataIntegrityViolationToGenericConflictWithoutSql() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/integrity"));

        expectProblem(result, 409, "Conflict", "Conflicting update", "/probe/integrity")
                .andExpect(content().string(not(containsString("SELECT"))));
    }

    @Test
    void mapsUnexpectedExceptionToGenericServerErrorWithoutLeakingDetails() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/boom"));

        expectProblem(result, 500, "Internal Server Error", "An unexpected error occurred", "/probe/boom")
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static ResultActions expectProblem(
            ResultActions result, int status, String title, String detail, String instance) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance));
    }
}
