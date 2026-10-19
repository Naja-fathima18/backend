package com.app.backend.library.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Year;

public class NotAfterCurrentYearValidator implements ConstraintValidator<NotAfterCurrentYear, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value <= Year.now().getValue();
    }
}
