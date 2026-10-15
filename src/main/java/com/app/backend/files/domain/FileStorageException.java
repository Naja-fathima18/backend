package com.app.backend.files.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class FileStorageException extends ApiException {

    public FileStorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
        initCause(cause);
    }
}
