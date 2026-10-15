package com.app.backend.common.error;

import java.util.Optional;

public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String message) {
        this(null, message);
    }

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public Optional<String> field() {
        return Optional.ofNullable(field);
    }
}
