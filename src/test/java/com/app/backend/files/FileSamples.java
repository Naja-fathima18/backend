package com.app.backend.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FileSamples {

    public static final byte[] PNG = withPadding(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    public static final byte[] JPEG = withPadding(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    public static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] EXE = withPadding(new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00});
    public static final byte[] TEXT = "just some plain text\n".getBytes(StandardCharsets.US_ASCII);

    private FileSamples() {}

    public static byte[] pngOfSize(int size) {
        byte[] content = Arrays.copyOf(PNG, size);
        Arrays.fill(content, PNG.length, size, (byte) 7);
        return content;
    }

    private static byte[] withPadding(byte[] signature) {
        byte[] content = Arrays.copyOf(signature, signature.length + 32);
        for (int i = signature.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }
}
