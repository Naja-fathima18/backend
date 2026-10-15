package com.app.backend.files.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends ApiException {

    public UnsupportedFileTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
