package com.app.backend.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotScheduleTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);

    private final SlotSchedule utcSchedule = new SlotSchedule(ZoneOffset.UTC, LocalTime.of(9, 0), LocalTime.of(17, 0));

    @Test
    void generatesThirtyMinuteSlotsWithinWorkingHours() {
        List<Slot> slots = utcSchedule.slotsOn(DAY);

        assertThat(slots).hasSize(16);
        assertThat(slots.getFirst())
                .isEqualTo(new Slot(Instant.parse("2030-01-07T09:00:00Z"), Instant.parse("2030-01-07T09:30:00Z")));
        assertThat(slots.getLast())
                .isEqualTo(new Slot(Instant.parse("2030-01-07T16:30:00Z"), Instant.parse("2030-01-07T17:00:00Z")));
    }

    @Test
    void doesNotGenerateSlotThatWouldEndAfterClosing() {
        SlotSchedule schedule = new SlotSchedule(ZoneOffset.UTC, LocalTime.of(9, 0), LocalTime.of(10, 15));

        List<Slot> slots = schedule.slotsOn(DAY);

        assertThat(slots)
                .extracting(Slot::startTime)
                .containsExactly(Instant.parse("2030-01-07T09:00:00Z"), Instant.parse("2030-01-07T09:30:00Z"));
    }

    @Test
    void generatesSlotsInTheConfiguredZone() {
        SlotSchedule kolkata = new SlotSchedule(ZoneId.of("Asia/Kolkata"), LocalTime.of(9, 0), LocalTime.of(17, 0));

        List<Slot> slots = kolkata.slotsOn(DAY);

        assertThat(slots.getFirst().startTime()).isEqualTo(Instant.parse("2030-01-07T03:30:00Z"));
    }

    @Test
    void findsSlotStartingAtAnAlignedTimeWithinHours() {
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T09:00:00Z")))
                .isPresent();
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T16:30:00Z")))
                .isPresent();
    }

    @Test
    void rejectsMisalignedOrOutOfHoursStartTimes() {
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T09:15:00Z")))
                .isEmpty();
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T09:00:00.001Z")))
                .isEmpty();
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T08:30:00Z")))
                .isEmpty();
        assertThat(utcSchedule.slotStartingAt(Instant.parse("2030-01-07T17:00:00Z")))
                .isEmpty();
    }
}
