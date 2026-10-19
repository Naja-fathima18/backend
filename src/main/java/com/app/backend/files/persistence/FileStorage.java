package com.app.backend.files.persistence;

import com.app.backend.files.config.FilesProperties;
import com.app.backend.files.domain.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(FilesProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
    }

    public UUID store(InputStream content) {
        UUID key = UUID.randomUUID();
        Path target = resolve(key.toString());
        try {
            Files.createDirectories(root);
            Files.copy(content, target);
        } catch (IOException ex) {
            deleteQuietly(key);
            throw new FileStorageException("Could not store file " + key, ex);
        }
        return key;
    }

    public Optional<Path> find(UUID key) {
        Path path = resolve(key.toString());
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public void delete(UUID key) {
        try {
            Files.deleteIfExists(resolve(key.toString()));
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file " + key, ex);
        }
    }

    public void deleteQuietly(UUID key) {
        try {
            Files.deleteIfExists(resolve(key.toString()));
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", key, ex);
        }
    }

    Path resolve(String storageName) {
        Path candidate = root.resolve(storageName).normalize();
        if (!root.equals(candidate.getParent())) {
            throw new FileStorageException("Resolved path is outside the storage root");
        }
        return candidate;
    }
}
