package com.app.backend.files.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.config.FilesProperties;
import com.app.backend.files.domain.FileStorageException;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.FileType;
import com.app.backend.files.domain.OriginalFileName;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.persistence.FileStorage;
import com.app.backend.files.persistence.StoredFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    static final String FILE_PART = "file";

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

    private final StoredFileRepository repository;
    private final FileStorage storage;
    private final FilesProperties properties;

    public FileService(StoredFileRepository repository, FileStorage storage, FilesProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public StoredFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(FILE_PART, "must not be empty");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new FileTooLargeException(properties.maxSize());
        }
        String originalName = OriginalFileName.sanitize(file.getOriginalFilename())
                .orElseThrow(() -> new BadRequestException(FILE_PART, "must have a file name"));
        FileType type = detectType(file);
        requireMatchingExtension(originalName, type);

        UUID storageKey = storeContent(file);
        deleteContentOnRollback(storageKey);
        StoredFile saved =
                repository.saveAndFlush(new StoredFile(originalName, type.contentType(), file.getSize(), storageKey));
        log.info("Stored file {} ({} bytes)", saved.getId(), saved.getSize());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<StoredFile> list(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public StoredFile get(UUID id) {
        return findExisting(id);
    }

    @Transactional(readOnly = true)
    public FileDownload download(UUID id) {
        StoredFile file = findExisting(id);
        Path content = storage.find(file.getStorageKey())
                .orElseThrow(() -> new NotFoundException("Content of file " + id + " was not found"));
        return new FileDownload(file.getOriginalName(), file.getContentType(), new FileSystemResource(content));
    }

    @Transactional
    public void delete(UUID id) {
        StoredFile file = findExisting(id);
        repository.delete(file);
        repository.flush();
        storage.delete(file.getStorageKey());
        log.info("Deleted file {}", id);
    }

    private StoredFile findExisting(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("File " + id + " was not found"));
    }

    private FileType detectType(MultipartFile file) {
        byte[] header;
        try (InputStream content = file.getInputStream()) {
            header = content.readNBytes(FileType.SIGNATURE_LENGTH);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read uploaded file", ex);
        }
        return FileType.detect(header).orElseThrow(UnsupportedFileTypeException::notAllowed);
    }

    private static void requireMatchingExtension(String originalName, FileType type) {
        boolean mismatch = OriginalFileName.extension(originalName)
                .filter(extension -> !type.acceptsExtension(extension))
                .isPresent();
        if (mismatch) {
            throw UnsupportedFileTypeException.extensionMismatch(type);
        }
    }

    private UUID storeContent(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return storage.store(content);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read uploaded file", ex);
        }
    }

    private void deleteContentOnRollback(UUID storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.deleteQuietly(storageKey);
                }
            }
        });
    }
}
