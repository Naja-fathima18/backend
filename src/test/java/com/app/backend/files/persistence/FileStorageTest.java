package com.app.backend.files.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.files.FileSamples;
import com.app.backend.files.domain.FileStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileStorageTest {

    @TempDir
    Path storageDir;

    @Test
    void writesReadsAndDeletesInsideTheStorageDirectory() throws Exception {
        FileStorage storage = new FileStorage(storageDir);
        byte[] content = {1, 2, 3};

        Path written = storage.write("abc.png", new ByteArrayInputStream(content));

        assertThat(written.getParent()).isEqualTo(storageDir.toAbsolutePath().normalize());
        assertThat(storage.read("abc.png").getContentAsByteArray()).isEqualTo(content);
        assertThat(storage.exists("abc.png")).isTrue();

        storage.delete("abc.png");

        assertThat(storage.exists("abc.png")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.png", "..", ".", "sub/../../escape.pdf"})
    void refusesNamesThatEscapeTheStorageDirectory(String name) {
        FileStorage storage = new FileStorage(storageDir);

        assertThatThrownBy(() -> storage.resolve(name)).isInstanceOf(FileStorageException.class);
    }

    @Test
    void removesPartiallyWrittenFileWhenTheStreamFails() {
        FileStorage storage = new FileStorage(storageDir);

        assertThatThrownBy(() -> storage.write("partial.png", FileSamples.failingAfter(new byte[64 * 1024])))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("Could not store the file")
                .hasCauseInstanceOf(IOException.class);

        assertThat(storageDir.resolve("partial.png")).doesNotExist();
    }

    @Test
    void reportsStorageDirectoryThatIsARegularFile() throws Exception {
        Path occupied = Files.writeString(storageDir.resolve("occupied"), "x");
        FileStorage storage = new FileStorage(occupied);

        assertThatThrownBy(() -> storage.write("a.png", new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(FileStorageException.class)
                .hasCauseInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(occupied)).isEqualTo("x");
    }

    @Test
    void refusesToOverwriteAnExistingFile() throws Exception {
        FileStorage storage = new FileStorage(storageDir);
        Files.write(storageDir.resolve("taken.pdf"), new byte[] {9});

        assertThatThrownBy(() -> storage.write("taken.pdf", new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(FileStorageException.class);
        assertThat(Files.readAllBytes(storageDir.resolve("taken.pdf"))).containsExactly(9);
    }
}
