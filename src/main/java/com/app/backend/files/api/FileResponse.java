package com.app.backend.files.api;

import com.app.backend.files.domain.StoredFile;
import java.time.Instant;

public record FileResponse(Long id, String originalName, String contentType, long size, Instant uploadedAt) {

    static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getFileType().mediaType(),
                file.getSizeBytes(),
                file.getUploadedAt());
    }
}
