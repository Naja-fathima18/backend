package com.app.backend.ratelimit.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(
        @DefaultValue("10") @Min(1) int limit,
        @DefaultValue("1m") @NotNull Duration window,
        @DefaultValue("/api/quotes/**") @NotEmpty List<@NotBlank String> paths) {

    @AssertTrue(message = "window must be positive")
    public boolean isWindowPositive() {
        return window != null && window.isPositive();
    }
}
