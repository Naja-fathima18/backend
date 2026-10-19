package com.app.backend.files.api;

import com.app.backend.files.domain.StoredFile;
import java.util.List;
import org.springframework.data.domain.Page;

public record StoredFilePageResponse(
        List<StoredFileResponse> content, int page, int size, long totalElements, int totalPages) {

    static StoredFilePageResponse from(Page<StoredFile> page) {
        return new StoredFilePageResponse(
                page.getContent().stream().map(StoredFileResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
