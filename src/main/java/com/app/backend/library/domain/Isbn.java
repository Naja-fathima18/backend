package com.app.backend.library.domain;

import java.util.Locale;

public final class Isbn {

    public static final String PATTERN = "^\\s*(?:(?:\\d-?){9}[\\dXx]|(?:\\d-?){12}\\d)\\s*$";

    private Isbn() {}

    public static String normalize(String raw) {
        return raw.strip().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
