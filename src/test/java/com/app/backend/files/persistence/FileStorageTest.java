package com.app.backend.files.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.files.FileSamples;
import com.app.backend.files.config.FilesProperties;
import com.app.backend.files.domain.FileStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class FileStorageTest {

    @TempDir
    private Path tempDir;

    private Path root;
    private FileStorage storage;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("uploads").resolve("files");
        storage = new FileStorage(new FilesProperties(root, DataSize.ofMegabytes(5)));
    }

    @Test
    void storesContentUnderGeneratedNameInsideRoot() throws IOException {
        UUID key = storage.store(new ByteArrayInputStream(FileSamples.PNG));

        Path stored = root.resolve(key.toString());
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(FileSamples.PNG);
        try (var entries = Files.list(root)) {
            assertThat(entries).containsExactly(stored);
        }
    }

    @Test
    void findsStoredContent() {
        UUID key = storage.store(new ByteArrayInputStream(FileSamples.PDF));

        assertThat(storage.find(key)).contains(root.resolve(key.toString()));
    }

    @Test
    void findsNothingForMissingContent() {
        assertThat(storage.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletesStoredContent() {
        UUID key = storage.store(new ByteArrayInputStream(FileSamples.PDF));

        storage.delete(key);

        assertThat(root.resolve(key.toString())).doesNotExist();
    }

    @Test
    void deleteQuietlyIgnoresMissingContent() {
        UUID key = UUID.randomUUID();

        storage.deleteQuietly(key);

        assertThat(root.resolve(key.toString())).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../evil.png", "../evil.png", "..", "sub/../../evil.png", "nested/file.png"})
    void refusesToResolveOutsideRoot(String storageName) {
        assertThatThrownBy(() -> storage.resolve(storageName))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("Resolved path is outside the storage root");
    }

    @Test
    void resolvesPlainNameDirectlyInsideRoot() {
        Path resolved = storage.resolve("abc");

        assertThat(resolved.getParent()).isEqualTo(root.toAbsolutePath().normalize());
    }
}
