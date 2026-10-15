package com.app.backend.booking.persistence;

import com.app.backend.booking.domain.Booking;
import com.app.backend.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b.startTime from Booking b
            where b.doctorId = :doctorId
              and b.slotDate = :slotDate
              and (b.status = :confirmed or (b.status = :held and b.expiresAt > :now))
            """)
    List<LocalTime> findTakenStartTimes(
            @Param("doctorId") Long doctorId,
            @Param("slotDate") LocalDate slotDate,
            @Param("now") Instant now,
            @Param("held") BookingStatus held,
            @Param("confirmed") BookingStatus confirmed);

    default List<LocalTime> findTakenStartTimes(Long doctorId, LocalDate slotDate, Instant now) {
        return findTakenStartTimes(doctorId, slotDate, now, BookingStatus.HELD, BookingStatus.CONFIRMED);
    }

    boolean existsByActiveSlotKey(String activeSlotKey);

    long countByActiveSlotKey(String activeSlotKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b
               set b.status = :expired, b.activeSlotKey = null, b.updatedAt = :now, b.version = b.version + 1
             where b.status = :held and b.expiresAt <= :now and b.activeSlotKey = :activeSlotKey
            """)
    int expireHoldForSlot(
            @Param("activeSlotKey") String activeSlotKey,
            @Param("now") Instant now,
            @Param("held") BookingStatus held,
            @Param("expired") BookingStatus expired);

    default int expireHoldForSlot(String activeSlotKey, Instant now) {
        return expireHoldForSlot(activeSlotKey, now, BookingStatus.HELD, BookingStatus.EXPIRED);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b
               set b.status = :expired, b.activeSlotKey = null, b.updatedAt = :now, b.version = b.version + 1
             where b.status = :held and b.expiresAt <= :now
            """)
    int expireOverdueHolds(
            @Param("now") Instant now, @Param("held") BookingStatus held, @Param("expired") BookingStatus expired);

    default int expireOverdueHolds(Instant now) {
        return expireOverdueHolds(now, BookingStatus.HELD, BookingStatus.EXPIRED);
    }
}
