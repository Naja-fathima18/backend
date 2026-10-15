package com.app.backend.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestController
@RequestMapping("/probe")
class ErrorProbeController {

    static final String SECRET = "secret-internal-detail SELECT * FROM users";

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    record ProbeRequest(
            @NotBlank String name, @NotNull @Min(1) Integer quantity) {}

    @PostMapping("/body")
    ProbeRequest body(@Valid @RequestBody ProbeRequest request) {
        return request;
    }

    @GetMapping("/params")
    int params(@RequestParam @Min(1) int size) {
        return size;
    }

    @GetMapping("/required")
    String required(@RequestParam String name) {
        return name;
    }

    @GetMapping("/items/{id}")
    long item(@PathVariable Long id) {
        return id;
    }

    @GetMapping("/constraint-violation")
    void constraintViolation() {
        throw new ConstraintViolationException(VALIDATOR.validate(new ProbeRequest("", 0)));
    }

    @GetMapping("/upload-too-large")
    void uploadTooLarge() {
        throw new MaxUploadSizeExceededException(1024);
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Book 42 not found");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Slot already booked");
    }

    @GetMapping("/bad-request")
    void badRequest() {
        throw new BadRequestException("Range is invalid");
    }

    @GetMapping("/bad-request-field")
    void badRequestWithField() {
        throw new BadRequestException("endDate", "must be after startDate");
    }

    @GetMapping("/optimistic-lock")
    void optimisticLock() {
        throw new ObjectOptimisticLockingFailureException(SECRET, 1L);
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new DataIntegrityViolationException(SECRET);
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException(SECRET);
    }
}
