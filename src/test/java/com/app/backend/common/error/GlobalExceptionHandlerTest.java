package com.app.backend.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    private static final String SECRET_MESSAGE = "db password is hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidBody() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"car\",\"count\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsFieldErrorsForInvalidBody() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.VALIDATION_FAILED_DETAIL))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 1"))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[1].message").value("must not be blank"));
    }

    @Test
    void returnsBadRequestWithoutParserDetailsForMalformedJson() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.MALFORMED_BODY_DETAIL))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("JSON parse error"))))
                .andExpect(content().string(not(containsString("com.fasterxml"))));
    }

    @Test
    void returnsBadRequestForMissingBody() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.MALFORMED_BODY_DETAIL));
    }

    @Test
    void returnsFieldErrorForConstraintViolatingParameter() throws Exception {
        mockMvc.perform(get("/probe/params").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.VALIDATION_FAILED_DETAIL))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 1"));
    }

    @Test
    void returnsFieldErrorForMissingParameter() throws Exception {
        mockMvc.perform(get("/probe/params"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.MISSING_PARAMETER_DETAIL))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
    }

    @Test
    void returnsFieldErrorForParameterOfWrongType() throws Exception {
        mockMvc.perform(get("/probe/params").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.INVALID_PARAMETER_DETAIL))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid int"))
                .andExpect(content().string(not(containsString("NumberFormatException"))));
    }

    @Test
    void returnsFieldErrorsForConstraintViolationException() throws Exception {
        mockMvc.perform(get("/probe/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.VALIDATION_FAILED_DETAIL))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[1].field").value("name"));
    }

    @Test
    void returnsFieldErrorForInvalidRequestExceptionWithField() throws Exception {
        mockMvc.perform(get("/probe/invalid-field"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("must not be in the future"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("exitTime"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be in the future"));
    }

    @Test
    void returnsDetailWithoutErrorsForInvalidRequestExceptionWithoutField() throws Exception {
        mockMvc.perform(get("/probe/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("exit time is before entry time"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returnsGenericServerErrorWithoutExceptionMessage() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.UNEXPECTED_ERROR_DETAIL))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET_MESSAGE))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    record ProbePayload(
            @NotBlank String name, @NotNull @Min(1) Integer count) {}

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        private final Validator validator;

        ProbeController(Validator validator) {
            this.validator = validator;
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody ProbePayload payload) {
            return payload.name();
        }

        @GetMapping("/params")
        int params(@RequestParam @Min(1) int count) {
            return count;
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new ProbePayload("", 0)));
        }

        @GetMapping("/invalid-field")
        void invalidField() {
            throw new InvalidRequestException("exitTime", "must not be in the future");
        }

        @GetMapping("/invalid")
        void invalid() {
            throw new InvalidRequestException("exit time is before entry time");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException(SECRET_MESSAGE);
        }
    }
}
