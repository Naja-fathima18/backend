package com.app.backend.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";
    static final String VALIDATION_FAILED = "Validation failed";
    static final String MALFORMED_BODY = "Malformed request body";
    static final String UPLOAD_TOO_LARGE = "Uploaded file exceeds the maximum allowed size";
    static final String CONFLICTING_UPDATE = "Conflicting update";
    static final String UNEXPECTED_ERROR = "An unexpected error occurred";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.add(new ApiFieldError(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> errors.add(new ApiFieldError(error.getObjectName(), error.getDefaultMessage())));
        return handleExceptionInternal(ex, badRequest(VALIDATION_FAILED, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError ? fieldError.getField() : parameterName;
                errors.add(new ApiFieldError(field, error.getDefaultMessage()));
            }
        });
        ProblemDetail body = badRequest(VALIDATION_FAILED, errors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body =
                badRequest(VALIDATION_FAILED, List.of(new ApiFieldError(ex.getParameterName(), "is required")));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field =
                ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        ProblemDetail body = badRequest(VALIDATION_FAILED, List.of(new ApiFieldError(field, "has an invalid value")));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, badRequest(MALFORMED_BODY, List.of()), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.PAYLOAD_TOO_LARGE, UPLOAD_TOO_LARGE);
        return handleExceptionInternal(ex, body, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(leafName(violation), violation.getMessage()))
                .toList();
        return respond(badRequest(VALIDATION_FAILED, errors));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail body = problem(ex.getStatus(), ex.getMessage());
        if (ex.getStatus() == HttpStatus.BAD_REQUEST || !ex.getErrors().isEmpty()) {
            body.setProperty(ERRORS_PROPERTY, ex.getErrors());
        }
        return respond(body);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> handleConflictingUpdate(Exception ex) {
        log.warn("Conflicting update rejected: {}", ex.getClass().getSimpleName());
        return respond(problem(HttpStatus.CONFLICT, CONFLICTING_UPDATE));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR));
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus()).body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        return body;
    }

    private static ProblemDetail badRequest(String detail, List<ApiFieldError> errors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, detail);
        body.setProperty(ERRORS_PROPERTY, errors);
        return body;
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
