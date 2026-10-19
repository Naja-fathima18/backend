package com.app.backend.library.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotAfterCurrentYearValidator.class)
public @interface NotAfterCurrentYear {

    String message() default "must not be after the current year";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
