package com.app.backend.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotGridTest {

    private final SlotGrid grid = new SlotGrid(LocalTime.of(9, 0), LocalTime.of(17, 0), Duration.ofMinutes(30));

    @Test
    void listsEveryThirtyMinuteStartWithinWorkingHours() {
        List<LocalTime> startTimes = grid.startTimes();

        assertThat(startTimes).hasSize(16);
        assertThat(startTimes.getFirst()).isEqualTo(LocalTime.of(9, 0));
        assertThat(startTimes.get(1)).isEqualTo(LocalTime.of(9, 30));
        assertThat(startTimes.getLast()).isEqualTo(LocalTime.of(16, 30));
    }

    @Test
    void dropsTrailingSlotThatWouldEndAfterDayEnd() {
        SlotGrid shortDay = new SlotGrid(LocalTime.of(9, 0), LocalTime.of(10, 15), Duration.ofMinutes(30));

        assertThat(shortDay.startTimes()).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(shortDay.contains(LocalTime.of(10, 0))).isFalse();
    }

    @Test
    void followsConfiguredSlotLength() {
        SlotGrid hourly = new SlotGrid(LocalTime.of(8, 0), LocalTime.of(11, 0), Duration.ofMinutes(60));

        assertThat(hourly.startTimes()).containsExactly(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(hourly.contains(LocalTime.of(8, 30))).isFalse();
    }

    @Test
    void acceptsAlignedStartsInsideWorkingHours() {
        assertThat(grid.contains(LocalTime.of(9, 0))).isTrue();
        assertThat(grid.contains(LocalTime.of(13, 30))).isTrue();
        assertThat(grid.contains(LocalTime.of(16, 30))).isTrue();
    }

    @Test
    void rejectsMisalignedOrOutOfHoursStarts() {
        assertThat(grid.contains(LocalTime.of(9, 15))).isFalse();
        assertThat(grid.contains(LocalTime.of(9, 0, 1))).isFalse();
        assertThat(grid.contains(LocalTime.of(8, 30))).isFalse();
        assertThat(grid.contains(LocalTime.of(17, 0))).isFalse();
    }
}
