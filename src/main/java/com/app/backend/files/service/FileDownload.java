package com.app.backend.files.service;

import com.app.backend.files.domain.FileType;
import org.springframework.core.io.Resource;

public record FileDownload(String originalName, FileType fileType, long size, Resource content) {}
