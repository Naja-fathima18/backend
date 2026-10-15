package com.app.backend.parking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.common.error.InvalidRequestException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClockTimeTest {

    private static final String ARABIC_INDIC_NINE_O_CLOCK = "٠٩:٠٠";

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "09:05", "12:30", "19:59", "23:59"})
    void parsesTwoDigitHoursAndMinutes(String value) {
        LocalTime time = ClockTime.parse("entry", value);

        assertThat(ClockTime.format(time)).isEqualTo(value);
    }

    @Test
    void parsesToMatchingLocalTime() {
        LocalTime time = ClockTime.parse("entry", "07:45");

        assertThat(time).isEqualTo(LocalTime.of(7, 45));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "9:00",
                "09:0",
                "24:00",
                "25:00",
                "09:60",
                "09:00:00",
                "0900",
                "09-00",
                " 09:00",
                "09:00 ",
                "ab:cd",
                "-1:00",
                "+9:00",
                ARABIC_INDIC_NINE_O_CLOCK
            })
    void rejectsValuesThatAreNotStrictHhMm(String value) {
        assertThatThrownBy(() -> ClockTime.parse("exit", value))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> {
                    assertThat(ex.field()).contains("exit");
                    assertThat(ex.getMessage()).isEqualTo(ClockTime.INVALID_TIME_MESSAGE);
                });
    }
}
