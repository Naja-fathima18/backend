package com.app.backend.library.domain;

import java.util.Optional;
import java.util.regex.Pattern;

public final class Isbn {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");
    private static final Pattern ISBN_10 = Pattern.compile("\\d{9}[\\dX]");
    private static final Pattern ISBN_13 = Pattern.compile("\\d{13}");

    private Isbn() {}

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String candidate = SEPARATORS.matcher(raw).replaceAll("").toUpperCase();
        if (ISBN_10.matcher(candidate).matches() && hasValidIsbn10Checksum(candidate)) {
            return Optional.of(candidate);
        }
        if (ISBN_13.matcher(candidate).matches() && hasValidIsbn13Checksum(candidate)) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static boolean hasValidIsbn10Checksum(String digits) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = digits.charAt(i);
            int value = c == 'X' ? 10 : c - '0';
            sum += (10 - i) * value;
        }
        return sum % 11 == 0;
    }

    private static boolean hasValidIsbn13Checksum(String digits) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int value = digits.charAt(i) - '0';
            sum += i % 2 == 0 ? value : value * 3;
        }
        return sum % 10 == 0;
    }
}
