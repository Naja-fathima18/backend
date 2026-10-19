package com.app.backend.booking.domain;

import com.app.backend.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "patient_id", nullable = false, length = 64)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {}

    private Booking(Long doctorId, Slot slot, String patientId, Instant holdExpiresAt) {
        this.doctorId = doctorId;
        this.startTime = slot.startTime();
        this.endTime = slot.endTime();
        this.patientId = patientId;
        this.status = BookingStatus.HELD;
        this.holdExpiresAt = holdExpiresAt;
    }

    public static Booking hold(Long doctorId, Slot slot, String patientId, Instant holdExpiresAt) {
        return new Booking(doctorId, slot, patientId, holdExpiresAt);
    }

    public boolean belongsTo(String candidatePatientId) {
        return patientId.equals(candidatePatientId);
    }

    public boolean isHoldExpiredAt(Instant now) {
        return status == BookingStatus.HELD && !now.isBefore(holdExpiresAt);
    }

    public BookingStatus statusAt(Instant now) {
        return isHoldExpiredAt(now) ? BookingStatus.EXPIRED : status;
    }

    public void confirm() {
        requireStatus(BookingStatus.HELD);
        status = BookingStatus.CONFIRMED;
        holdExpiresAt = null;
    }

    public void expire() {
        requireStatus(BookingStatus.HELD);
        status = BookingStatus.EXPIRED;
    }

    public void cancel() {
        requireStatus(BookingStatus.CONFIRMED);
        status = BookingStatus.CANCELLED;
    }

    private void requireStatus(BookingStatus expected) {
        if (status != expected) {
            throw new ConflictException("Booking " + id + " is " + status + ", expected " + expected);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getPatientId() {
        return patientId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }
}
