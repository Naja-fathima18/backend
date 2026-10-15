package com.app.backend.files.domain;

public final class OriginalFileName {

    public static final int MAX_LENGTH = 255;

    private static final int MAX_KEPT_EXTENSION_LENGTH = 16;

    private OriginalFileName() {}

    public static String sanitize(String rawName) {
        if (rawName == null) {
            return "";
        }
        int lastSeparator = Math.max(rawName.lastIndexOf('/'), rawName.lastIndexOf('\\'));
        String lastSegment = rawName.substring(lastSeparator + 1);
        String withoutControlChars = lastSegment
                .codePoints()
                .filter(OriginalFileName::isVisible)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        return truncate(withoutControlChars);
    }

    private static boolean isVisible(int codePoint) {
        return !Character.isISOControl(codePoint) && Character.getType(codePoint) != Character.FORMAT;
    }

    private static String truncate(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        boolean keepExtension = dot > 0 && name.length() - dot <= MAX_KEPT_EXTENSION_LENGTH;
        String extension = keepExtension ? name.substring(dot) : "";
        int baseLength = MAX_LENGTH - extension.length();
        if (Character.isHighSurrogate(name.charAt(baseLength - 1))) {
            baseLength--;
        }
        return name.substring(0, baseLength) + extension;
    }
}
