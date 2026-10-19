package com.app.backend.booking.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SlotSchedule {

    public static final Duration SLOT_LENGTH = Duration.ofMinutes(30);

    private final ZoneId zone;
    private final LocalTime openingTime;
    private final LocalTime closingTime;

    public SlotSchedule(ZoneId zone, LocalTime openingTime, LocalTime closingTime) {
        this.zone = zone;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    public ZoneId zone() {
        return zone;
    }

    public List<Slot> slotsOn(LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        LocalTime start = openingTime;
        while (fitsBeforeClosing(start)) {
            Instant startInstant = ZonedDateTime.of(date, start, zone).toInstant();
            slots.add(new Slot(startInstant, startInstant.plus(SLOT_LENGTH)));
            start = start.plus(SLOT_LENGTH);
        }
        return slots;
    }

    public Optional<Slot> slotStartingAt(Instant startTime) {
        LocalDate date = startTime.atZone(zone).toLocalDate();
        return slotsOn(date).stream()
                .filter(slot -> slot.startTime().equals(startTime))
                .findFirst();
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    public Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private boolean fitsBeforeClosing(LocalTime start) {
        LocalTime end = start.plus(SLOT_LENGTH);
        return !end.isAfter(closingTime) && end.isAfter(start);
    }
}
