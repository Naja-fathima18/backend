package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingProperties;
import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.Slot;
import com.app.backend.booking.domain.SlotSchedule;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.booking.persistence.BookingSlotRepository;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository slotRepository;
    private final DoctorService doctorService;
    private final ApplicationEventPublisher eventPublisher;
    private final SlotSchedule schedule;
    private final Duration holdDuration;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSlotRepository slotRepository,
            DoctorService doctorService,
            ApplicationEventPublisher eventPublisher,
            BookingProperties properties,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.doctorService = doctorService;
        this.eventPublisher = eventPublisher;
        this.schedule = new SlotSchedule(properties.zone(), properties.openingTime(), properties.closingTime());
        this.holdDuration = properties.holdDuration();
        this.clock = clock;
    }

    @Transactional
    public BookingView hold(Long doctorId, Instant startTime, String patientId) {
        Instant now = clock.instant();
        doctorService.requireDoctorExists(doctorId);
        Slot slot = requireBookableSlot(startTime, now);

        slotRepository.insertIfAbsent(doctorId, slot.startTime());
        Booking booking =
                bookingRepository.saveAndFlush(Booking.hold(doctorId, slot, patientId, now.plus(holdDuration)));
        int claimed =
                slotRepository.claim(doctorId, slot.startTime(), booking.getId(), booking.getHoldExpiresAt(), now);
        if (claimed == 0) {
            throw new ConflictException("Slot is not available");
        }
        bookingRepository.expireStaleHoldsForSlot(doctorId, slot.startTime(), now);
        log.info("Booking {} holds doctor {} slot at {}", booking.getId(), doctorId, slot.startTime());
        return BookingView.of(booking, now);
    }

    @Transactional(readOnly = true)
    public BookingView get(Long bookingId) {
        return BookingView.of(findBooking(bookingId), clock.instant());
    }

    @Transactional(noRollbackFor = HoldExpiredException.class)
    public BookingView confirm(Long bookingId, String patientId) {
        Instant now = clock.instant();
        Booking booking = findOwnedBooking(bookingId, patientId);
        if (booking.isHoldExpiredAt(now)) {
            booking.expire();
            slotRepository.release(bookingId);
            throw new HoldExpiredException(bookingId);
        }
        booking.confirm();
        if (slotRepository.confirmHold(bookingId, now) == 0) {
            throw new ConflictException("Hold for booking " + bookingId + " is no longer valid");
        }
        eventPublisher.publishEvent(
                new BookingConfirmedEvent(booking.getId(), booking.getDoctorId(), booking.getStartTime()));
        return BookingView.of(booking, now);
    }

    @Transactional
    public BookingView cancel(Long bookingId, String patientId) {
        Booking booking = findOwnedBooking(bookingId, patientId);
        booking.cancel();
        slotRepository.release(bookingId);
        log.info("Booking {} cancelled", bookingId);
        return BookingView.of(booking, clock.instant());
    }

    private Slot requireBookableSlot(Instant startTime, Instant now) {
        if (startTime.isBefore(now)) {
            throw new BadRequestException("startTime", "must not be in the past");
        }
        return schedule.slotStartingAt(startTime)
                .orElseThrow(() -> new BadRequestException(
                        "startTime", "must be the start of a 30-minute slot within working hours"));
    }

    private Booking findOwnedBooking(Long bookingId, String patientId) {
        Booking booking = findBooking(bookingId);
        if (!booking.belongsTo(patientId)) {
            throw new BookingOwnershipException(bookingId);
        }
        return booking;
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
    }
}
