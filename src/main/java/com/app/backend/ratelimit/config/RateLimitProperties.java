package com.app.backend.ratelimit.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(
        @Positive @DefaultValue("10") int capacity,
        @NotNull @DefaultValue("1m") Duration window,
        @NotBlank @DefaultValue("X-API-Key") String headerName,
        @NotEmpty @DefaultValue("/api/quotes/**") List<@NotBlank String> pathPatterns) {

    public RateLimitProperties {
        if (window != null && (window.isZero() || window.isNegative())) {
            throw new IllegalArgumentException("rate-limit.window must be positive");
        }
        pathPatterns = pathPatterns == null ? null : List.copyOf(pathPatterns);
    }
}
