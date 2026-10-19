package com.app.backend.files.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum FileType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", Set.of("png"), new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", Set.of("pdf"), "%PDF-".getBytes(StandardCharsets.US_ASCII));

    public static final int SIGNATURE_LENGTH = 8;

    private final String contentType;
    private final Set<String> extensions;
    private final byte[] signature;

    FileType(String contentType, Set<String> extensions, byte[] signature) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.signature = signature;
    }

    public static Optional<FileType> detect(byte[] header) {
        return Arrays.stream(values()).filter(type -> type.matches(header)).findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public boolean acceptsExtension(String extension) {
        return extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    private boolean matches(byte[] header) {
        return header.length >= signature.length
                && Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
    }
}
