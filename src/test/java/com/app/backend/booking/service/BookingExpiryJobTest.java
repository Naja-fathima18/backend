package com.app.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.app.backend.booking.config.BookingSchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BookingExpiryJobTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(BookingService.class, () -> mock(BookingService.class))
            .withUserConfiguration(BookingSchedulingConfig.class, BookingExpiryJob.class);

    @Test
    void sweepIsScheduledByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(BookingExpiryJob.class));
    }

    @Test
    void sweepCanBeDisabledByProperty() {
        runner.withPropertyValues("booking.expiry-sweep-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(BookingExpiryJob.class));
    }

    @Test
    void sweepDelegatesToTheService() {
        BookingService bookingService = mock(BookingService.class);

        new BookingExpiryJob(bookingService).expireOverdueHolds();

        verify(bookingService).expireOverdueHolds();
    }
}
