package com.app.backend.files.service;

import com.app.backend.files.persistence.FileStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FilesProperties.class)
class FilesConfiguration {

    @Bean
    FileStorage fileStorage(FilesProperties properties) {
        return new FileStorage(properties.storageDir());
    }
}
