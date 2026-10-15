package com.app.backend.parking.domain;

import com.app.backend.common.error.InvalidRequestException;

public class ExitBeforeEntryException extends InvalidRequestException {

    public static final String FIELD = "exit";
    public static final String MESSAGE = "must not be before entry";

    public ExitBeforeEntryException() {
        super(FIELD, MESSAGE);
    }
}
