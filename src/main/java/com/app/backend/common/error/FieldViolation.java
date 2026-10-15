package com.app.backend.common.error;

import java.util.Comparator;

public record FieldViolation(String field, String message) {

    static final Comparator<FieldViolation> ORDER =
            Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message);
}
