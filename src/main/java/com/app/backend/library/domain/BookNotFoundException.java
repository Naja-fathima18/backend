package com.app.backend.library.domain;

import com.app.backend.common.error.NotFoundException;

public class BookNotFoundException extends NotFoundException {

    public BookNotFoundException(Long id) {
        super("Book " + id + " not found");
    }
}
