package com.app.backend.library.domain;

import com.app.backend.common.error.ConflictException;

public class BookNotBorrowedException extends ConflictException {

    public BookNotBorrowedException(Long id) {
        super("Book " + id + " is not currently borrowed");
    }
}
