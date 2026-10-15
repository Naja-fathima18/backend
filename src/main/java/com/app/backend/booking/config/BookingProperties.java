package com.app.backend.booking.config;

import com.app.backend.booking.domain.SlotGrid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("booking")
public record BookingProperties(
        @NotNull @DefaultValue("5m") Duration holdDuration,
        @NotNull @DefaultValue("30m") Duration slotLength,
        @NotNull @DefaultValue("09:00") LocalTime dayStart,
        @NotNull @DefaultValue("17:00") LocalTime dayEnd,
        @NotNull @DefaultValue("30s") Duration expirySweepInterval,
        @DefaultValue("true") boolean expirySweepEnabled) {

    public BookingProperties {
        requirePositive("booking.hold-duration", holdDuration);
        requirePositive("booking.slot-length", slotLength);
        requirePositive("booking.expiry-sweep-interval", expirySweepInterval);
        if (dayStart != null && dayEnd != null && !dayStart.isBefore(dayEnd)) {
            throw new IllegalArgumentException("booking.day-start must be before booking.day-end");
        }
    }

    public SlotGrid slotGrid() {
        return new SlotGrid(dayStart, dayEnd, slotLength);
    }

    private static void requirePositive(String name, Duration value) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
