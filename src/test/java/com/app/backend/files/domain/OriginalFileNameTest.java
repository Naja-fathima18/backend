package com.app.backend.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OriginalFileNameTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "../../etc/passwd.png|passwd.png",
                "..\\evil.pdf|evil.pdf",
                "C:\\Users\\me\\photo.jpg|photo.jpg",
                "/abs/path/to/doc.pdf|doc.pdf",
                "mixed/..\\x.png|x.png",
                "plain.png|plain.png"
            })
    void keepsOnlyTheLastPathSegment(String raw, String expected) {
        assertThat(OriginalFileName.sanitize(raw)).isEqualTo(expected);
    }

    @Test
    void dropsControlCharacters() {
        assertThat(OriginalFileName.sanitize("re\u0000port\r\n\t.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void returnsEmptyForNullOrDirectoryOnlyNames() {
        assertThat(OriginalFileName.sanitize(null)).isEmpty();
        assertThat(OriginalFileName.sanitize("../")).isEmpty();
        assertThat(OriginalFileName.sanitize("  ")).isEmpty();
    }

    @Test
    void keepsUnicodeAndQuotes() {
        assertThat(OriginalFileName.sanitize("r\u00e9sum\u00e9 \"final\".pdf"))
                .isEqualTo("r\u00e9sum\u00e9 \"final\".pdf");
    }

    @Test
    void limitsLengthWhileKeepingTheExtension() {
        String sanitized = OriginalFileName.sanitize("a".repeat(400) + ".png");

        assertThat(sanitized).hasSize(OriginalFileName.MAX_LENGTH).endsWith(".png");
    }

    @Test
    void keepsNameOfExactlyMaxLength() {
        String name = "b".repeat(OriginalFileName.MAX_LENGTH - 4) + ".pdf";

        assertThat(OriginalFileName.sanitize(name)).isEqualTo(name);
    }
}
