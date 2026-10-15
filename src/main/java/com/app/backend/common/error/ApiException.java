package com.app.backend.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ApiFieldError> getErrors() {
        return List.of();
    }
}
