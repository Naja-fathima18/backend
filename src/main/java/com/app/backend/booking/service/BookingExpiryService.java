package com.app.backend.booking.service;

import com.app.backend.booking.persistence.BookingRepository;
import com.app.backend.booking.persistence.BookingSlotRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository slotRepository;
    private final Clock clock;

    public BookingExpiryService(
            BookingRepository bookingRepository, BookingSlotRepository slotRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    @Transactional
    public int expireStaleHolds() {
        Instant now = clock.instant();
        slotRepository.releaseExpiredHolds(now);
        return bookingRepository.expireAllStaleHolds(now);
    }
}
