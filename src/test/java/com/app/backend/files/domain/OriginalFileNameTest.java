package com.app.backend.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OriginalFileNameTest {

    @Test
    void stripsUnixPathTraversal() {
        assertThat(OriginalFileName.sanitize("../../etc/passwd")).contains("passwd");
    }

    @Test
    void stripsWindowsPathComponents() {
        assertThat(OriginalFileName.sanitize("C:\\Users\\me\\..\\photo.png")).contains("photo.png");
    }

    @Test
    void stripsControlCharacters() {
        assertThat(OriginalFileName.sanitize("re\r\nport\u0000.pdf")).contains("report.pdf");
    }

    @Test
    void stripsInvisibleFormatCharacters() {
        assertThat(OriginalFileName.sanitize("invoice\u202Egnp.pdf")).contains("invoicegnp.pdf");
    }

    @Test
    void keepsNonAsciiAndQuotes() {
        assertThat(OriginalFileName.sanitize("résumé \"final\".pdf")).contains("résumé \"final\".pdf");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "../", "..", ".", "dir/", "\u0000\u0001"})
    void yieldsNothingWhenNoUsableNameRemains(String raw) {
        assertThat(OriginalFileName.sanitize(raw)).isEmpty();
    }

    @Test
    void truncatesLongNamesToMaxLengthKeepingExtension() {
        String raw = "a".repeat(400) + ".png";

        String sanitized = OriginalFileName.sanitize(raw).orElseThrow();

        assertThat(sanitized).hasSize(OriginalFileName.MAX_LENGTH).endsWith("a.png");
    }

    @Test
    void keepsNameOfExactlyMaxLength() {
        String raw = "b".repeat(OriginalFileName.MAX_LENGTH - 4) + ".pdf";

        assertThat(OriginalFileName.sanitize(raw)).contains(raw);
    }

    @Test
    void doesNotSplitSurrogatePairWhenTruncating() {
        String raw = "a".repeat(250) + "\uD83D\uDE00".repeat(10) + ".png";

        String sanitized = OriginalFileName.sanitize(raw).orElseThrow();

        assertThat(sanitized).isEqualTo("a".repeat(250) + ".png");
    }

    @Test
    void extractsLowerCasedExtension() {
        assertThat(OriginalFileName.extension("Photo.PNG")).contains("png");
    }

    @Test
    void extractsLastExtensionOnly() {
        assertThat(OriginalFileName.extension("virus.exe.png")).contains("png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"README", "trailing."})
    void hasNoExtensionWithoutSuffix(String name) {
        assertThat(OriginalFileName.extension(name)).isEmpty();
    }
}
