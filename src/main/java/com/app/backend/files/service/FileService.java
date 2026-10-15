package com.app.backend.files.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.domain.FileStorageException;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.FileType;
import com.app.backend.files.domain.OriginalFileName;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.persistence.FileStorage;
import com.app.backend.files.persistence.StoredFileRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final StoredFileRepository repository;
    private final FileStorage storage;
    private final TransactionOperations transactions;
    private final long maxSizeBytes;

    public FileService(
            StoredFileRepository repository,
            FileStorage storage,
            TransactionOperations transactions,
            FilesProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.transactions = transactions;
        this.maxSizeBytes = properties.maxSize().toBytes();
    }

    public StoredFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file", "must not be empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new FileTooLargeException("File exceeds the maximum size of " + maxSizeBytes + " bytes");
        }
        String originalName = OriginalFileName.sanitize(file.getOriginalFilename());
        if (originalName.isEmpty()) {
            throw new BadRequestException("file", "must have a file name");
        }
        try (InputStream content = new BufferedInputStream(file.getInputStream())) {
            FileType fileType = detectType(content, originalName);
            String storageName = UUID.randomUUID() + "." + fileType.storageExtension();
            storage.write(storageName, content);
            StoredFile saved = saveOrDiscard(new StoredFile(storageName, originalName, fileType, file.getSize()));
            log.info("Stored file {} as {}", saved.getId(), storageName);
            return saved;
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the uploaded file", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<StoredFile> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public FileDownload download(long id) {
        StoredFile file = findOrThrow(id);
        if (!storage.exists(file.getStorageName())) {
            log.warn("Content of stored file {} is missing", id);
            throw notFound(id);
        }
        return new FileDownload(
                file.getOriginalName(), file.getFileType(), file.getSizeBytes(), storage.read(file.getStorageName()));
    }

    public void delete(long id) {
        String storageName = transactions.execute(status -> {
            StoredFile file = findOrThrow(id);
            if (repository.removeById(id) == 0) {
                throw notFound(id);
            }
            return file.getStorageName();
        });
        storage.delete(storageName);
        log.info("Deleted file {}", id);
    }

    private FileType detectType(InputStream content, String originalName) throws IOException {
        content.mark(FileType.SIGNATURE_LENGTH);
        byte[] leadingBytes = content.readNBytes(FileType.SIGNATURE_LENGTH);
        content.reset();
        FileType fileType = FileType.detect(leadingBytes)
                .orElseThrow(() -> new UnsupportedFileTypeException("Only JPEG, PNG and PDF files are allowed"));
        if (!fileType.acceptsFileName(originalName)) {
            throw new UnsupportedFileTypeException(
                    "File extension does not match its content, which is " + fileType.name());
        }
        return fileType;
    }

    private StoredFile saveOrDiscard(StoredFile file) {
        try {
            return transactions.execute(status -> repository.saveAndFlush(file));
        } catch (RuntimeException ex) {
            storage.deleteQuietly(file.getStorageName());
            throw ex;
        }
    }

    private StoredFile findOrThrow(long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("File " + id + " not found");
    }
}
