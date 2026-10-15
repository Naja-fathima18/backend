package com.app.backend.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    private final String field;

    public BadRequestException(String message) {
        this(null, message);
    }

    public BadRequestException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    @Override
    public List<ApiFieldError> getErrors() {
        return field == null ? List.of() : List.of(new ApiFieldError(field, getMessage()));
    }
}
