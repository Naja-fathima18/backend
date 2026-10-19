package com.app.backend.ratelimit.api;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class MissingApiKeyException extends ApiException {

    public MissingApiKeyException() {
        super(HttpStatus.UNAUTHORIZED, "Missing or blank " + RateLimitInterceptor.API_KEY_HEADER + " header");
    }
}
