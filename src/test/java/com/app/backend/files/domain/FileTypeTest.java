package com.app.backend.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.files.FileSamples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileTypeTest {

    @Test
    void detectsPngFromMagicBytes() {
        assertThat(FileType.detect(FileSamples.PNG)).contains(FileType.PNG);
    }

    @Test
    void detectsJpegFromMagicBytes() {
        assertThat(FileType.detect(FileSamples.JPEG)).contains(FileType.JPEG);
    }

    @Test
    void detectsPdfFromMagicBytes() {
        assertThat(FileType.detect(FileSamples.PDF)).contains(FileType.PDF);
    }

    @Test
    void detectsNothingForExecutable() {
        assertThat(FileType.detect(FileSamples.EXE)).isEmpty();
    }

    @Test
    void detectsNothingForPlainText() {
        assertThat(FileType.detect(FileSamples.TEXT)).isEmpty();
    }

    @Test
    void detectsNothingForTruncatedSignature() {
        byte[] partialPng = {(byte) 0x89, 0x50, 0x4E, 0x47};

        assertThat(FileType.detect(partialPng)).isEmpty();
    }

    @Test
    void detectsNothingForEmptyHeader() {
        assertThat(FileType.detect(new byte[0])).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "JPG", "JpEg"})
    void jpegAcceptsItsExtensionsCaseInsensitively(String extension) {
        assertThat(FileType.JPEG.acceptsExtension(extension)).isTrue();
    }

    @Test
    void pdfRejectsPngExtension() {
        assertThat(FileType.PDF.acceptsExtension("png")).isFalse();
    }

    @Test
    void exposesDetectedContentTypes() {
        assertThat(FileType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(FileType.PNG.contentType()).isEqualTo("image/png");
        assertThat(FileType.PDF.contentType()).isEqualTo("application/pdf");
    }
}
