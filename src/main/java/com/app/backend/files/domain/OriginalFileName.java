package com.app.backend.files.domain;

import java.util.Locale;
import java.util.Optional;

public final class OriginalFileName {

    public static final int MAX_LENGTH = 255;

    private OriginalFileName() {}

    public static Optional<String> sanitize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String lastSegment = lastPathSegment(withoutControlCharacters(raw)).strip();
        if (lastSegment.isEmpty() || lastSegment.equals(".") || lastSegment.equals("..")) {
            return Optional.empty();
        }
        return Optional.of(truncate(lastSegment));
    }

    public static Optional<String> extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String withoutControlCharacters(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints().filter(codePoint -> !isControl(codePoint)).forEach(cleaned::appendCodePoint);
        return cleaned.toString();
    }

    private static boolean isControl(int codePoint) {
        return Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT;
    }

    private static String lastPathSegment(String value) {
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return value.substring(separator + 1);
    }

    private static String truncate(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String suffix = dot > 0 && name.length() - dot < MAX_LENGTH ? name.substring(dot) : "";
        int baseLength = MAX_LENGTH - suffix.length();
        if (Character.isHighSurrogate(name.charAt(baseLength - 1))) {
            baseLength--;
        }
        return name.substring(0, baseLength) + suffix;
    }
}
