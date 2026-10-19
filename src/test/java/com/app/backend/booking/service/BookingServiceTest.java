package com.app.backend.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.backend.booking.config.BookingProperties;
import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import com.app.backend.booking.domain.Slot;
import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.booking.persistence.BookingSlotRepository;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");
    private static final Instant START = Instant.parse("2030-01-07T10:00:00Z");
    private static final Slot SLOT = new Slot(START, START.plus(Duration.ofMinutes(30)));

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSlotRepository slotRepository;

    @Mock
    private DoctorService doctorService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingService service;

    @BeforeEach
    void setUp() {
        BookingProperties properties = new BookingProperties(
                ZoneOffset.UTC,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                Duration.ofMinutes(5),
                new BookingProperties.ExpirySweep(false, Duration.ofMinutes(1)));
        service = new BookingService(
                bookingRepository,
                slotRepository,
                doctorService,
                eventPublisher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void holdFailsWithConflictWhenSlotCannotBeClaimed() {
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slotRepository.claim(eq(1L), eq(START), any(), any(), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> service.hold(1L, START, "alice")).isInstanceOf(ConflictException.class);

        verify(bookingRepository, never()).expireStaleHoldsForSlot(anyLong(), any(), any());
    }

    @Test
    void holdRejectsPastStartTimeBeforeTouchingTheDatabase() {
        Instant past = Instant.parse("2030-01-06T10:00:00Z");

        assertThatThrownBy(() -> service.hold(1L, past, "alice")).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(slotRepository, bookingRepository);
    }

    @Test
    void holdPropagatesUnknownDoctor() {
        doThrow(new NotFoundException("Doctor 9 not found")).when(doctorService).requireDoctorExists(9L);

        assertThatThrownBy(() -> service.hold(9L, START, "alice")).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(slotRepository, bookingRepository);
    }

    @Test
    void confirmFailsWithoutPublishingWhenSlotHoldIsNoLongerValid() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW.plus(Duration.ofMinutes(5)));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(slotRepository.confirmHold(7L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(7L, "alice")).isInstanceOf(ConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPublishesEventWhenHoldIsValid() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW.plus(Duration.ofMinutes(5)));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(slotRepository.confirmHold(7L, NOW)).thenReturn(1);

        BookingView view = service.confirm(7L, "alice");

        assertThat(view.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(eventPublisher).publishEvent(new BookingConfirmedEvent(null, 1L, START));
    }

    @Test
    void confirmOfExpiredHoldMarksItExpiredAndReleasesSlot() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirm(7L, "alice")).isInstanceOf(HoldExpiredException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(slotRepository).release(7L);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmByAnotherPatientIsRejectedWithoutChanges() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW.plus(Duration.ofMinutes(5)));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.confirm(7L, "bob")).isInstanceOf(BookingOwnershipException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verifyNoInteractions(slotRepository, eventPublisher);
    }

    @Test
    void cancelReleasesSlotOfConfirmedBooking() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW.plus(Duration.ofMinutes(5)));
        booking.confirm();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        BookingView view = service.cancel(7L, "alice");

        assertThat(view.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(slotRepository).release(7L);
    }

    @Test
    void cancelOfHeldBookingIsConflictAndKeepsSlot() {
        Booking booking = Booking.hold(1L, SLOT, "alice", NOW.plus(Duration.ofMinutes(5)));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(7L, "alice")).isInstanceOf(ConflictException.class);

        verify(slotRepository, never()).release(anyLong());
    }
}
