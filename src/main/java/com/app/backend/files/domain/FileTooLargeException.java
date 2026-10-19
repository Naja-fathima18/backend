package com.app.backend.files.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException(DataSize maxSize) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size of " + describe(maxSize));
    }

    private static String describe(DataSize size) {
        if (size.toBytes() % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + " MB";
        }
        return size.toBytes() + " bytes";
    }
}
