package com.app.backend.booking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class BookingPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(BookingProperties.class)
    static class PropertiesOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    void defaultsMatchTheSpecification() {
        runner.run(context -> {
            BookingProperties properties = context.getBean(BookingProperties.class);

            assertThat(properties.holdDuration()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.slotLength()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.dayStart()).isEqualTo(LocalTime.of(9, 0));
            assertThat(properties.dayEnd()).isEqualTo(LocalTime.of(17, 0));
            assertThat(properties.expirySweepInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.expirySweepEnabled()).isTrue();
        });
    }

    @Test
    void bindsHoldDurationSlotLengthAndWorkingHoursFromConfiguration() {
        runner.withPropertyValues(
                        "booking.hold-duration=10m",
                        "booking.slot-length=1h",
                        "booking.day-start=08:00",
                        "booking.day-end=12:00",
                        "booking.expiry-sweep-interval=5s",
                        "booking.expiry-sweep-enabled=false")
                .run(context -> {
                    BookingProperties properties = context.getBean(BookingProperties.class);

                    assertThat(properties.holdDuration()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.slotGrid().startTimes())
                            .containsExactly(
                                    LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
                    assertThat(properties.expirySweepInterval()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.expirySweepEnabled()).isFalse();
                });
    }

    @Test
    void rejectsWorkingDayThatEndsBeforeItStarts() {
        runner.withPropertyValues("booking.day-start=17:00", "booking.day-end=09:00")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveHoldDuration() {
        runner.withPropertyValues("booking.hold-duration=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
