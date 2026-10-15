package com.app.backend.parking.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

public final class ParkingTariff {

    public static final int FREE_STAY_MINUTES = 30;
    public static final int RUPEES_PER_STARTED_HOUR = 20;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int MAX_FEE_PER_VISIT_RUPEES = 100;

    private ParkingTariff() {}

    public static int visitFee(LocalTime entry, LocalTime exit) {
        return feeForDuration(durationMinutes(entry, exit));
    }

    public static int durationMinutes(LocalTime entry, LocalTime exit) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(exit, "exit");
        if (exit.isBefore(entry)) {
            throw new ExitBeforeEntryException();
        }
        return Math.toIntExact(Duration.between(entry, exit).toMinutes());
    }

    public static int feeForDuration(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (minutes <= FREE_STAY_MINUTES) {
            return 0;
        }
        int startedHours = Math.ceilDiv(minutes, MINUTES_PER_HOUR);
        return Math.min(startedHours * RUPEES_PER_STARTED_HOUR, MAX_FEE_PER_VISIT_RUPEES);
    }
}
