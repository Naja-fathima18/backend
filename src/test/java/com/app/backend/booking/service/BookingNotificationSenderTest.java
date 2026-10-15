package com.app.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.booking.domain.BookingConfirmedEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BookingNotificationSenderTest {

    @Test
    void logsConfirmationWithoutPatientDetails(CapturedOutput output) {
        BookingNotificationSender sender = new BookingNotificationSender();

        sender.sendConfirmation(new BookingConfirmedEvent(42L, 2L, LocalDate.of(2030, 1, 10), LocalTime.of(9, 30)));

        assertThat(output).contains("Booking 42 confirmed for doctor 2 at 2030-01-10 09:30");
    }
}
