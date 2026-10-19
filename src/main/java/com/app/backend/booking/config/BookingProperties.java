package com.app.backend.booking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @NotNull @DefaultValue("UTC") ZoneId zone,
        @NotNull @DefaultValue("09:00") LocalTime openingTime,
        @NotNull @DefaultValue("17:00") LocalTime closingTime,
        @NotNull @DefaultValue("PT5M") Duration holdDuration,
        @Valid @NotNull @DefaultValue ExpirySweep expirySweep) {

    public BookingProperties {
        if (openingTime != null && closingTime != null && !openingTime.isBefore(closingTime)) {
            throw new IllegalArgumentException("booking.opening-time must be before booking.closing-time");
        }
        requirePositive(holdDuration, "booking.hold-duration");
    }

    public record ExpirySweep(
            @DefaultValue("true") boolean enabled,
            @NotNull @DefaultValue("PT1M") Duration interval) {

        public ExpirySweep {
            requirePositive(interval, "booking.expiry-sweep.interval");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
