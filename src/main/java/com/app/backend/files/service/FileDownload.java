package com.app.backend.files.service;

import org.springframework.core.io.Resource;

public record FileDownload(String originalName, String contentType, Resource content) {}
