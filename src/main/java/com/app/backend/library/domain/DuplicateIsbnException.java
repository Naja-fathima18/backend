package com.app.backend.library.domain;

import com.app.backend.common.error.ConflictException;

public class DuplicateIsbnException extends ConflictException {

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN " + isbn + " already exists");
    }
}
