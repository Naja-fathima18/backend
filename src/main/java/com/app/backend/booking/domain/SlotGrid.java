package com.app.backend.booking.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public record SlotGrid(LocalTime dayStart, LocalTime dayEnd, Duration slotLength) {

    public List<LocalTime> startTimes() {
        List<LocalTime> startTimes = new ArrayList<>();
        LocalTime start = dayStart;
        while (fitsBeforeDayEnd(start)) {
            startTimes.add(start);
            start = start.plus(slotLength);
        }
        return startTimes;
    }

    public boolean contains(LocalTime startTime) {
        if (startTime.isBefore(dayStart) || !fitsBeforeDayEnd(startTime)) {
            return false;
        }
        long offsetNanos = Duration.between(dayStart, startTime).toNanos();
        return offsetNanos % slotLength.toNanos() == 0;
    }

    public long slotLengthMinutes() {
        return slotLength.toMinutes();
    }

    private boolean fitsBeforeDayEnd(LocalTime start) {
        long nanosUntilDayEnd = Duration.between(start, dayEnd).toNanos();
        return nanosUntilDayEnd >= slotLength.toNanos();
    }
}
