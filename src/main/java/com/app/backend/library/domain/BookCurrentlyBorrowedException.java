package com.app.backend.library.domain;

import com.app.backend.common.error.ConflictException;

public class BookCurrentlyBorrowedException extends ConflictException {

    public BookCurrentlyBorrowedException(Long id) {
        super("Book " + id + " is currently borrowed and cannot be deleted");
    }
}
