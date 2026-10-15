package com.app.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.booking.MutableClock;
import com.app.backend.booking.config.BookingProperties;
import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingConfirmedEvent;
import com.app.backend.booking.domain.BookingNotFoundException;
import com.app.backend.booking.domain.BookingStateConflictException;
import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.domain.DoctorNotFoundException;
import com.app.backend.booking.domain.HoldExpiredException;
import com.app.backend.booking.domain.SlotUnavailableException;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.common.error.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Long DOCTOR_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2030, 1, 10);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final Instant NOW = Instant.parse("2030-01-10T07:00:00Z");
    private static final String SLOT_KEY = "1|2030-01-10|09:00";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private DoctorService doctorService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);

    private final BookingProperties properties = new BookingProperties(
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            Duration.ofSeconds(30),
            true);

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, doctorService, properties, eventPublisher, clock);
    }

    private HoldRequest holdAt(LocalDate date, LocalTime startTime) {
        return new HoldRequest(DOCTOR_ID, date, startTime, "  Jane Doe ");
    }

    private Booking heldBooking() {
        return Booking.hold(DOCTOR_ID, DATE, NINE, "Jane Doe", NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void availableSlotsExcludeTakenStartTimes() {
        when(bookingRepository.findTakenStartTimes(DOCTOR_ID, DATE, NOW))
                .thenReturn(List.of(NINE, LocalTime.of(13, 30)));

        AvailableSlots slots = service.availableSlots(DOCTOR_ID, DATE);

        assertThat(slots.startTimes()).hasSize(14).doesNotContain(NINE, LocalTime.of(13, 30));
        assertThat(slots.startTimes()).contains(LocalTime.of(9, 30), LocalTime.of(16, 30));
    }

    @Test
    void availableSlotsExcludeSlotsThatAlreadyStarted() {
        clock.set(Instant.parse("2030-01-10T12:10:00Z"));
        when(bookingRepository.findTakenStartTimes(any(), any(), any())).thenReturn(List.of());

        AvailableSlots slots = service.availableSlots(DOCTOR_ID, DATE);

        assertThat(slots.startTimes().getFirst()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    void availableSlotsForPastDateAreEmpty() {
        when(bookingRepository.findTakenStartTimes(any(), any(), any())).thenReturn(List.of());

        AvailableSlots slots = service.availableSlots(DOCTOR_ID, DATE.minusDays(1));

        assertThat(slots.startTimes()).isEmpty();
    }

    @Test
    void availableSlotsRejectUnknownDoctor() {
        doThrow(new DoctorNotFoundException(9L)).when(doctorService).requireExists(9L);

        assertThatThrownBy(() -> service.availableSlots(9L, DATE)).isInstanceOf(DoctorNotFoundException.class);
    }

    @Test
    void holdCreatesHeldBookingExpiringAfterHoldDuration() {
        when(bookingRepository.existsByActiveSlotKey(SLOT_KEY)).thenReturn(false);

        BookingView view = service.hold(holdAt(DATE, NINE));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPatientName()).isEqualTo("Jane Doe");
        assertThat(saved.getValue().getActiveSlotKey()).isEqualTo(SLOT_KEY);
        assertThat(view.status()).isEqualTo(BookingStatus.HELD);
        assertThat(view.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void holdReleasesExpiredHoldOnTheSlotBeforeInserting() {
        when(bookingRepository.existsByActiveSlotKey(SLOT_KEY)).thenReturn(false);

        service.hold(holdAt(DATE, NINE));

        verify(bookingRepository).expireHoldForSlot(SLOT_KEY, NOW);
    }

    @Test
    void holdRejectsUnknownDoctor() {
        doThrow(new DoctorNotFoundException(DOCTOR_ID)).when(doctorService).requireExists(DOCTOR_ID);

        assertThatThrownBy(() -> service.hold(holdAt(DATE, NINE))).isInstanceOf(DoctorNotFoundException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void holdRejectsStartOffTheGrid() {
        assertThatThrownBy(() -> service.hold(holdAt(DATE, LocalTime.of(9, 15))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("30-minute slot grid");
    }

    @Test
    void holdRejectsStartOutsideWorkingHours() {
        assertThatThrownBy(() -> service.hold(holdAt(DATE, LocalTime.of(17, 0))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void holdRejectsSlotInThePast() {
        clock.set(Instant.parse("2030-01-10T09:00:00Z"));

        assertThatThrownBy(() -> service.hold(holdAt(DATE, NINE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must be in the future");
    }

    @Test
    void holdRejectsSlotWithActiveBooking() {
        when(bookingRepository.existsByActiveSlotKey(SLOT_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.hold(holdAt(DATE, NINE))).isInstanceOf(SlotUnavailableException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void holdMapsUniqueConstraintViolationToSlotUnavailable() {
        when(bookingRepository.existsByActiveSlotKey(anyString())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.hold(holdAt(DATE, NINE))).isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void confirmMarksHoldConfirmedAndPublishesEvent() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(heldBooking()));

        BookingView view = service.confirm(7L);

        assertThat(view.status()).isEqualTo(BookingStatus.CONFIRMED);
        ArgumentCaptor<BookingConfirmedEvent> event = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().doctorId()).isEqualTo(DOCTOR_ID);
        assertThat(event.getValue().startTime()).isEqualTo(NINE);
    }

    @Test
    void confirmOfExpiredHoldExpiresItAndPublishesNothing() {
        Booking booking = heldBooking();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.confirm(7L)).isInstanceOf(HoldExpiredException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getActiveSlotKey()).isNull();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void confirmTwiceIsConflict() {
        Booking booking = heldBooking();
        booking.confirm(NOW);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirm(7L)).isInstanceOf(BookingStateConflictException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void confirmUnknownBookingIsNotFound() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(7L)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancelReleasesConfirmedBooking() {
        Booking booking = heldBooking();
        booking.confirm(NOW);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        BookingView view = service.cancel(7L);

        assertThat(view.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getActiveSlotKey()).isNull();
    }

    @Test
    void cancelOfHeldBookingIsConflict() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(heldBooking()));

        assertThatThrownBy(() -> service.cancel(7L)).isInstanceOf(BookingStateConflictException.class);
    }

    @Test
    void cancelUnknownBookingIsNotFound() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(7L)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void expirySweepUsesCurrentClockTime() {
        when(bookingRepository.expireOverdueHolds(NOW)).thenReturn(3);

        int expired = service.expireOverdueHolds();

        assertThat(expired).isEqualTo(3);
    }
}
