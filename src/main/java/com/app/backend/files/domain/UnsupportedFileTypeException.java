package com.app.backend.files.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends ApiException {

    private UnsupportedFileTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public static UnsupportedFileTypeException notAllowed() {
        return new UnsupportedFileTypeException("Unsupported file type: only JPEG, PNG and PDF files are allowed");
    }

    public static UnsupportedFileTypeException extensionMismatch(FileType detected) {
        return new UnsupportedFileTypeException(
                "File extension does not match the file content, which was detected as " + detected.contentType());
    }
}
