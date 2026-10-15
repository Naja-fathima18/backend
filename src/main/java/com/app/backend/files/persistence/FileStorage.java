package com.app.backend.files.persistence;

import com.app.backend.files.domain.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path storageDir;

    public FileStorage(Path storageDir) {
        this.storageDir = storageDir.toAbsolutePath().normalize();
    }

    public Path write(String storageName, InputStream content) {
        Path target = resolve(storageName);
        createStorageDirectory();
        try {
            Files.copy(content, target);
            return target;
        } catch (FileAlreadyExistsException ex) {
            log.error("Refusing to overwrite stored file {}", storageName);
            throw new FileStorageException("Could not store the file", ex);
        } catch (IOException ex) {
            log.error("Failed to write stored file {}", storageName, ex);
            deleteQuietly(storageName);
            throw new FileStorageException("Could not store the file", ex);
        }
    }

    private void createStorageDirectory() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException ex) {
            log.error("Could not create storage directory {}", storageDir, ex);
            throw new FileStorageException("Could not store the file", ex);
        }
    }

    public Resource read(String storageName) {
        return new FileSystemResource(resolve(storageName));
    }

    public boolean exists(String storageName) {
        return Files.isRegularFile(resolve(storageName));
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(resolve(storageName));
        } catch (IOException ex) {
            log.error("Failed to delete stored file {}", storageName, ex);
            throw new FileStorageException("Could not delete the file", ex);
        }
    }

    public void deleteQuietly(String storageName) {
        try {
            Files.deleteIfExists(resolve(storageName));
        } catch (IOException ex) {
            log.warn("Could not clean up stored file {}", storageName, ex);
        }
    }

    public Path resolve(String storageName) {
        Path resolved = storageDir.resolve(storageName).normalize();
        if (!resolved.startsWith(storageDir) || resolved.equals(storageDir)) {
            throw new FileStorageException(
                    "Invalid storage location", new IllegalArgumentException("Path escapes the storage directory"));
        }
        return resolved;
    }
}
