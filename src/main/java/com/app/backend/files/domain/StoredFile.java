package com.app.backend.files.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stored_file")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_name", nullable = false, updatable = false, length = 64)
    private String storageName;

    @Column(name = "original_name", nullable = false, updatable = false, length = 255)
    private String originalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, updatable = false, length = 16)
    private FileType fileType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected StoredFile() {}

    public StoredFile(String storageName, String originalName, FileType fileType, long sizeBytes) {
        this.storageName = storageName;
        this.originalName = originalName;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public String getStorageName() {
        return storageName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
