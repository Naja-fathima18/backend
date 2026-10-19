package com.app.backend.files.api;

import com.app.backend.files.domain.StoredFile;
import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(UUID id, String originalName, String contentType, long size, Instant uploadedAt) {

    static StoredFileResponse from(StoredFile file) {
        return new StoredFileResponse(
                file.getId(), file.getOriginalName(), file.getContentType(), file.getSize(), file.getUploadedAt());
    }
}
