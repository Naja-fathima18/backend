package com.app.backend.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";
    static final String VALIDATION_FAILED_DETAIL = "Request validation failed.";
    static final String MALFORMED_BODY_DETAIL = "Request body is missing or malformed.";
    static final String INVALID_PARAMETER_DETAIL = "Request parameter has an invalid value.";
    static final String MISSING_PARAMETER_DETAIL = "Required request parameter is missing.";
    static final String UNEXPECTED_ERROR_DETAIL = "An unexpected error occurred.";
    static final String DEFAULT_VIOLATION_MESSAGE = "is invalid";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Stream<FieldViolation> fieldViolations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> violation(error.getField(), error.getDefaultMessage()));
        Stream<FieldViolation> objectViolations = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> violation(error.getObjectName(), error.getDefaultMessage()));
        ProblemDetail body = validationProblem(Stream.concat(fieldViolations, objectViolations));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Stream<FieldViolation> violations =
                ex.getParameterValidationResults().stream().flatMap(GlobalExceptionHandler::violationsOf);
        ProblemDetail body = validationProblem(violations);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = problem(MISSING_PARAMETER_DETAIL);
        body.setProperty(ERRORS_PROPERTY, List.of(violation(ex.getParameterName(), "is required")));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(INVALID_PARAMETER_DETAIL);
        body.setProperty(ERRORS_PROPERTY, List.of(violation(ex.getPropertyName(), typeMismatchMessage(ex))));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(MALFORMED_BODY_DETAIL);
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            body.setProperty(ERRORS_PROPERTY, List.of(violation(jsonPathOf(mismatch), mismatchMessage(mismatch))));
        }
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Stream<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> violation(leafName(violation), violation.getMessage()));
        return ResponseEntity.badRequest().body(validationProblem(violations));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException ex) {
        ProblemDetail body = problem(ex.getMessage());
        ex.field().ifPresent(field -> body.setProperty(ERRORS_PROPERTY, List.of(violation(field, ex.getMessage()))));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_DETAIL);
        return ResponseEntity.internalServerError().body(body);
    }

    private static ProblemDetail problem(String detail) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private static ProblemDetail validationProblem(Stream<FieldViolation> violations) {
        ProblemDetail body = problem(VALIDATION_FAILED_DETAIL);
        body.setProperty(
                ERRORS_PROPERTY, violations.sorted(FieldViolation.ORDER).toList());
        return body;
    }

    private static Stream<FieldViolation> violationsOf(ParameterValidationResult result) {
        String parameterName =
                Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter");
        return result.getResolvableErrors().stream().map(error -> violationOf(parameterName, error));
    }

    private static FieldViolation violationOf(String parameterName, MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return violation(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return violation(parameterName, error.getDefaultMessage());
    }

    private static String leafName(ConstraintViolation<?> violation) {
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .map(Path.Node::getName)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("parameter");
    }

    private static String jsonPathOf(MismatchedInputException mismatch) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : mismatch.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "body" : path.toString();
    }

    private static String mismatchMessage(MismatchedInputException mismatch) {
        Class<?> targetType = mismatch.getTargetType();
        if (targetType == null) {
            return DEFAULT_VIOLATION_MESSAGE;
        }
        return "must be a valid " + targetType.getSimpleName();
    }

    private static String typeMismatchMessage(TypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType == null) {
            return DEFAULT_VIOLATION_MESSAGE;
        }
        return "must be a valid " + requiredType.getSimpleName();
    }

    private static FieldViolation violation(String field, String message) {
        return new FieldViolation(
                Objects.requireNonNullElse(field, "parameter"),
                Objects.requireNonNullElse(message, DEFAULT_VIOLATION_MESSAGE));
    }
}
