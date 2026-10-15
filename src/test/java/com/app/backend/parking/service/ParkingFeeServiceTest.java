package com.app.backend.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.parking.domain.ExitBeforeEntryException;
import com.app.backend.parking.domain.VisitFee;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParkingFeeServiceTest {

    private final ParkingFeeService parkingFeeService = new ParkingFeeService();

    @ParameterizedTest(name = "{0}-{1} lasts {2} minutes and costs {3}")
    @CsvSource({
        "09:00, 09:30, 30, 0",
        "09:00, 10:00, 60, 20",
        "09:00, 10:01, 61, 40",
        "08:00, 17:00, 540, 100",
        "12:00, 12:00, 0, 0",
        "12:00, 12:31, 31, 20",
        "12:00, 17:00, 300, 100",
        "12:00, 17:01, 301, 100",
        "00:00, 23:59, 1439, 100"
    })
    void returnsDurationAndFeeForVisit(LocalTime entry, LocalTime exit, int expectedMinutes, int expectedFee) {
        VisitFee visitFee = parkingFeeService.visitFee(entry, exit);

        assertThat(visitFee).isEqualTo(new VisitFee(expectedMinutes, expectedFee));
    }

    @Test
    void rejectsVisitWhoseExitIsBeforeEntry() {
        LocalTime entry = LocalTime.of(9, 0);
        LocalTime exit = LocalTime.of(8, 0);

        assertThatThrownBy(() -> parkingFeeService.visitFee(entry, exit)).isInstanceOf(ExitBeforeEntryException.class);
    }
}
