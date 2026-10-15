package com.app.backend.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FileSamples {

    public static final int FIVE_MB = 5 * 1024 * 1024;

    private FileSamples() {}

    public static byte[] jpeg(int size) {
        return withSignature(size, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0);
    }

    public static byte[] png(int size) {
        return withSignature(size, (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    public static byte[] pdf(int size) {
        return withSignature(size, 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37);
    }

    public static byte[] windowsExecutable() {
        return withSignature(64, 0x4D, 0x5A, (byte) 0x90, 0x00);
    }

    public static byte[] text() {
        return "just some plain text".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withSignature(int size, int... signature) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 0x41);
        for (int i = 0; i < signature.length && i < size; i++) {
            content[i] = (byte) signature[i];
        }
        for (int i = signature.length; i < size; i++) {
            content[i] = (byte) (i % 251);
        }
        return content;
    }
}
