package com.app.backend.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.files.FileSamples;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileTypeTest {

    @Test
    void detectsJpegFromLeadingBytes() {
        assertThat(FileType.detect(leading(FileSamples.jpeg(32)))).contains(FileType.JPEG);
    }

    @Test
    void detectsPngFromLeadingBytes() {
        assertThat(FileType.detect(leading(FileSamples.png(32)))).contains(FileType.PNG);
    }

    @Test
    void detectsPdfFromLeadingBytes() {
        assertThat(FileType.detect(leading(FileSamples.pdf(32)))).contains(FileType.PDF);
    }

    @Test
    void detectsNothingForExecutableBytes() {
        assertThat(FileType.detect(leading(FileSamples.windowsExecutable()))).isEmpty();
    }

    @Test
    void detectsNothingForPlainText() {
        assertThat(FileType.detect(leading(FileSamples.text()))).isEmpty();
    }

    @Test
    void detectsNothingForTruncatedPngSignature() {
        byte[] truncated = Arrays.copyOf(FileSamples.png(32), 7);

        assertThat(FileType.detect(truncated)).isEmpty();
    }

    @Test
    void detectsNothingForEmptyInput() {
        assertThat(FileType.detect(new byte[0])).isEmpty();
    }

    @Test
    void detectsNothingWhenSignatureIsOffByOneByte() {
        byte[] almostPdf = leading(FileSamples.pdf(32));
        almostPdf[4] = 0x2E;

        assertThat(FileType.detect(almostPdf)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.JPEG", "a.b.jpeg"})
    void jpegAcceptsJpegExtensions(String name) {
        assertThat(FileType.JPEG.acceptsFileName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.png", "photo", "photo.", "jpg", "photo.jpg.exe"})
    void jpegRejectsOtherExtensions(String name) {
        assertThat(FileType.JPEG.acceptsFileName(name)).isFalse();
    }

    @Test
    void pdfRejectsPngExtension() {
        assertThat(FileType.PDF.acceptsFileName("photo.png")).isFalse();
    }

    private static byte[] leading(byte[] content) {
        return Arrays.copyOf(content, Math.min(content.length, FileType.SIGNATURE_LENGTH));
    }
}
