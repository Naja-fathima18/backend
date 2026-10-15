package com.app.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithGlobalExceptionHandler() {
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
    }
}
