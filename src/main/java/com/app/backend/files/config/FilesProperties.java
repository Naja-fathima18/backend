package com.app.backend.files.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.files")
public record FilesProperties(
        @NotNull Path storageDir, @NotNull DataSize maxSize) {}
