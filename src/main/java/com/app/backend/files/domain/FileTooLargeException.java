package com.app.backend.files.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
