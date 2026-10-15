package com.app.backend.files.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum FileType {
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", Set.of("png"), new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", "pdf", Set.of("pdf"), new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D});

    public static final int SIGNATURE_LENGTH = 8;

    private final String mediaType;
    private final String storageExtension;
    private final Set<String> acceptedExtensions;
    private final byte[] signature;

    FileType(String mediaType, String storageExtension, Set<String> acceptedExtensions, byte[] signature) {
        this.mediaType = mediaType;
        this.storageExtension = storageExtension;
        this.acceptedExtensions = acceptedExtensions;
        this.signature = signature;
    }

    public static Optional<FileType> detect(byte[] leadingBytes) {
        return Arrays.stream(values())
                .filter(type -> type.matches(leadingBytes))
                .findFirst();
    }

    public String mediaType() {
        return mediaType;
    }

    public String storageExtension() {
        return storageExtension;
    }

    public boolean acceptsFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return acceptedExtensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private boolean matches(byte[] leadingBytes) {
        if (leadingBytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (leadingBytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
