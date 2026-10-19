package com.app.backend.booking.persistence;

import com.app.backend.booking.domain.BookingSlot;
import com.app.backend.booking.domain.BookingSlotId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, BookingSlotId> {

    @Modifying
    @Query(value = """
                    insert into booking_slots (doctor_id, start_time, version)
                    values (:doctorId, :startTime, 0)
                    on conflict do nothing
                    """, nativeQuery = true)
    int insertIfAbsent(@Param("doctorId") Long doctorId, @Param("startTime") Instant startTime);

    @Modifying
    @Query("""
            update BookingSlot s
               set s.bookingId = :bookingId,
                   s.holdExpiresAt = :holdExpiresAt,
                   s.version = s.version + 1
             where s.id.doctorId = :doctorId
               and s.id.startTime = :startTime
               and (s.bookingId is null or s.holdExpiresAt <= :now)
            """)
    int claim(
            @Param("doctorId") Long doctorId,
            @Param("startTime") Instant startTime,
            @Param("bookingId") Long bookingId,
            @Param("holdExpiresAt") Instant holdExpiresAt,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update BookingSlot s
               set s.holdExpiresAt = null,
                   s.version = s.version + 1
             where s.bookingId = :bookingId
               and s.holdExpiresAt > :now
            """)
    int confirmHold(@Param("bookingId") Long bookingId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update BookingSlot s
               set s.bookingId = null,
                   s.holdExpiresAt = null,
                   s.version = s.version + 1
             where s.bookingId = :bookingId
            """)
    int release(@Param("bookingId") Long bookingId);

    @Modifying
    @Query("""
            update BookingSlot s
               set s.bookingId = null,
                   s.holdExpiresAt = null,
                   s.version = s.version + 1
             where s.holdExpiresAt <= :now
            """)
    int releaseExpiredHolds(@Param("now") Instant now);

    @Query("""
            select s.id.startTime
              from BookingSlot s
             where s.id.doctorId = :doctorId
               and s.id.startTime >= :from
               and s.id.startTime < :to
               and s.bookingId is not null
               and (s.holdExpiresAt is null or s.holdExpiresAt > :now)
            """)
    List<Instant> findTakenStartTimes(
            @Param("doctorId") Long doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("now") Instant now);
}
