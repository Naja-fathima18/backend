package com.app.backend.files.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FilesProperties.class)
public class FilesConfiguration {}
