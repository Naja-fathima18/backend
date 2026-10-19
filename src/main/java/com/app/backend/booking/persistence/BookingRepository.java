package com.app.backend.booking.persistence;

import com.app.backend.booking.domain.Booking;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Modifying
    @Query("""
            update Booking b
               set b.status = com.app.backend.booking.domain.BookingStatus.EXPIRED,
                   b.version = b.version + 1,
                   b.updatedAt = :now
             where b.doctorId = :doctorId
               and b.startTime = :startTime
               and b.status = com.app.backend.booking.domain.BookingStatus.HELD
               and b.holdExpiresAt <= :now
            """)
    int expireStaleHoldsForSlot(
            @Param("doctorId") Long doctorId, @Param("startTime") Instant startTime, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Booking b
               set b.status = com.app.backend.booking.domain.BookingStatus.EXPIRED,
                   b.version = b.version + 1,
                   b.updatedAt = :now
             where b.status = com.app.backend.booking.domain.BookingStatus.HELD
               and b.holdExpiresAt <= :now
            """)
    int expireAllStaleHolds(@Param("now") Instant now);
}
