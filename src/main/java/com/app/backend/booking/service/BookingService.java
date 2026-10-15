package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingConfig;
import com.app.backend.booking.config.BookingProperties;
import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingConfirmedEvent;
import com.app.backend.booking.domain.BookingNotFoundException;
import com.app.backend.booking.domain.HoldExpiredException;
import com.app.backend.booking.domain.SlotGrid;
import com.app.backend.booking.domain.SlotUnavailableException;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.common.error.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final DoctorService doctorService;
    private final BookingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            DoctorService doctorService,
            BookingProperties properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier(BookingConfig.CLOCK) Clock clock) {
        this.bookingRepository = bookingRepository;
        this.doctorService = doctorService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailableSlots availableSlots(Long doctorId, LocalDate date) {
        doctorService.requireExists(doctorId);
        Instant now = clock.instant();
        Set<LocalTime> taken = new HashSet<>(bookingRepository.findTakenStartTimes(doctorId, date, now));
        List<LocalTime> available = properties.slotGrid().startTimes().stream()
                .filter(startTime -> !taken.contains(startTime))
                .filter(startTime -> startsAfter(date, startTime, now))
                .toList();
        return new AvailableSlots(doctorId, date, available);
    }

    @Transactional(readOnly = true)
    public BookingView get(Long bookingId) {
        return BookingView.of(load(bookingId));
    }

    @Transactional
    public BookingView hold(HoldRequest request) {
        doctorService.requireExists(request.doctorId());
        Instant now = clock.instant();
        requireBookableSlot(request.date(), request.startTime(), now);
        String slotKey = Booking.slotKey(request.doctorId(), request.date(), request.startTime());
        if (bookingRepository.expireHoldForSlot(slotKey, now) > 0) {
            log.info("Released expired hold on slot {}", slotKey);
        }
        if (bookingRepository.existsByActiveSlotKey(slotKey)) {
            throw new SlotUnavailableException();
        }
        Booking booking = Booking.hold(
                request.doctorId(),
                request.date(),
                request.startTime(),
                request.patientName().strip(),
                now.plus(properties.holdDuration()));
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException concurrentHold) {
            log.info("Concurrent hold on slot {} rejected by the unique constraint", slotKey);
            throw new SlotUnavailableException();
        }
        log.info("Booking {} holds slot {} until {}", booking.getId(), slotKey, booking.getExpiresAt());
        return BookingView.of(booking);
    }

    @Transactional(noRollbackFor = HoldExpiredException.class)
    public BookingView confirm(Long bookingId) {
        Booking booking = load(bookingId);
        Instant now = clock.instant();
        if (booking.isHoldExpiredAt(now)) {
            booking.expire();
            log.info("Booking {} hold expired before confirmation", bookingId);
            throw new HoldExpiredException();
        }
        booking.confirm(now);
        bookingRepository.flush();
        eventPublisher.publishEvent(new BookingConfirmedEvent(
                booking.getId(), booking.getDoctorId(), booking.getSlotDate(), booking.getStartTime()));
        return BookingView.of(booking);
    }

    @Transactional
    public BookingView cancel(Long bookingId) {
        Booking booking = load(bookingId);
        booking.cancel();
        bookingRepository.flush();
        log.info("Booking {} cancelled", bookingId);
        return BookingView.of(booking);
    }

    @Transactional
    public int expireOverdueHolds() {
        int expired = bookingRepository.expireOverdueHolds(clock.instant());
        if (expired > 0) {
            log.info("Expired {} overdue holds", expired);
        }
        return expired;
    }

    private Booking load(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private void requireBookableSlot(LocalDate date, LocalTime startTime, Instant now) {
        SlotGrid grid = properties.slotGrid();
        if (!grid.contains(startTime)) {
            throw new BadRequestException(
                    "startTime",
                    "must align to the " + grid.slotLengthMinutes() + "-minute slot grid between " + grid.dayStart()
                            + " and " + grid.dayEnd());
        }
        if (!startsAfter(date, startTime, now)) {
            throw new BadRequestException("startTime", "must be in the future");
        }
    }

    private boolean startsAfter(LocalDate date, LocalTime startTime, Instant now) {
        return ZonedDateTime.of(date, startTime, clock.getZone()).toInstant().isAfter(now);
    }
}
