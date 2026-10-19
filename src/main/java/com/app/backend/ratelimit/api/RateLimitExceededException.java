package com.app.backend.ratelimit.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class RateLimitExceededException extends ErrorResponseException {

    static final String RETRY_AFTER_PROPERTY = "retryAfterSeconds";

    public RateLimitExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, problem(retryAfterSeconds), null);
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    private static ProblemDetail problem(long retryAfterSeconds) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded, retry after " + retryAfterSeconds + " seconds");
        body.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.setProperty(RETRY_AFTER_PROPERTY, retryAfterSeconds);
        return body;
    }
}
