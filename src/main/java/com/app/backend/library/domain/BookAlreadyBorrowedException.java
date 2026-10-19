package com.app.backend.library.domain;

import com.app.backend.common.error.ConflictException;

public class BookAlreadyBorrowedException extends ConflictException {

    public BookAlreadyBorrowedException(Long id) {
        super("Book " + id + " is already borrowed");
    }
}
