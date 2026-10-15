package com.app.backend.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParkingTariffTest {

    @ParameterizedTest(name = "{0}-{1} costs {2}")
    @CsvSource({"09:00, 09:30, 0", "09:00, 10:00, 20", "09:00, 10:01, 40", "08:00, 17:00, 100"})
    void chargesRequiredExamples(LocalTime entry, LocalTime exit, int expectedFee) {
        int fee = ParkingTariff.visitFee(entry, exit);

        assertThat(fee).isEqualTo(expectedFee);
    }

    @ParameterizedTest(name = "{0} minutes cost {1}")
    @CsvSource({
        "0, 0",
        "1, 0",
        "30, 0",
        "31, 20",
        "59, 20",
        "60, 20",
        "61, 40",
        "120, 40",
        "121, 60",
        "240, 80",
        "241, 100",
        "300, 100",
        "301, 100",
        "1439, 100"
    })
    void chargesTwentyPerStartedHourAfterFreeHalfHourCappedAtHundred(int minutes, int expectedFee) {
        int fee = ParkingTariff.feeForDuration(minutes);

        assertThat(fee).isEqualTo(expectedFee);
    }

    @ParameterizedTest(name = "{0}-{1} costs {2}")
    @CsvSource({
        "10:00, 10:00, 0",
        "10:00, 10:30, 0",
        "10:00, 10:31, 20",
        "10:00, 11:00, 20",
        "10:00, 11:01, 40",
        "10:00, 15:00, 100",
        "10:00, 15:01, 100",
        "00:00, 23:59, 100"
    })
    void chargesBoundaryStaysByClockTime(LocalTime entry, LocalTime exit, int expectedFee) {
        int fee = ParkingTariff.visitFee(entry, exit);

        assertThat(fee).isEqualTo(expectedFee);
    }

    @Test
    void measuresDurationInWholeMinutes() {
        int minutes = ParkingTariff.durationMinutes(LocalTime.of(0, 0), LocalTime.of(23, 59));

        assertThat(minutes).isEqualTo(1439);
    }

    @Test
    void rejectsExitBeforeEntry() {
        LocalTime entry = LocalTime.of(10, 0);
        LocalTime exit = LocalTime.of(9, 59);

        assertThatThrownBy(() -> ParkingTariff.visitFee(entry, exit))
                .isInstanceOf(ExitBeforeEntryException.class)
                .hasMessage(ExitBeforeEntryException.MESSAGE);
    }

    @Test
    void rejectsNegativeDuration() {
        assertThatThrownBy(() -> ParkingTariff.feeForDuration(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTimes() {
        LocalTime time = LocalTime.of(10, 0);

        assertThatThrownBy(() -> ParkingTariff.visitFee(null, time)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ParkingTariff.visitFee(time, null)).isInstanceOf(NullPointerException.class);
    }
}
