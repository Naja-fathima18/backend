package com.app.backend.parking.api;

import com.app.backend.common.error.InvalidRequestException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class ClockTime {

    public static final String INVALID_TIME_MESSAGE = "must be a time in HH:mm format between 00:00 and 23:59";

    private static final Pattern HH_MM = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private ClockTime() {}

    public static LocalTime parse(String field, String value) {
        if (value == null || !HH_MM.matcher(value).matches()) {
            throw new InvalidRequestException(field, INVALID_TIME_MESSAGE);
        }
        return LocalTime.parse(value, FORMAT);
    }

    public static String format(LocalTime time) {
        return time.format(FORMAT);
    }
}
