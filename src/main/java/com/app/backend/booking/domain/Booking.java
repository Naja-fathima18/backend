package com.app.backend.booking.domain;

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
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "patient_name", nullable = false, length = 100)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "active_slot_key", length = 64)
    private String activeSlotKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Booking() {}

    private Booking(Long doctorId, LocalDate slotDate, LocalTime startTime, String patientName, Instant expiresAt) {
        this.doctorId = doctorId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.patientName = patientName;
        this.status = BookingStatus.HELD;
        this.activeSlotKey = slotKey(doctorId, slotDate, startTime);
        this.expiresAt = expiresAt;
    }

    public static Booking hold(
            Long doctorId, LocalDate slotDate, LocalTime startTime, String patientName, Instant expiresAt) {
        return new Booking(doctorId, slotDate, startTime, patientName, expiresAt);
    }

    public static String slotKey(Long doctorId, LocalDate slotDate, LocalTime startTime) {
        return doctorId + "|" + slotDate + "|" + startTime;
    }

    public boolean isHoldExpiredAt(Instant now) {
        return status == BookingStatus.HELD && !now.isBefore(expiresAt);
    }

    public void expire() {
        requireStatus(BookingStatus.HELD, "expired");
        release(BookingStatus.EXPIRED);
    }

    public void confirm(Instant now) {
        requireStatus(BookingStatus.HELD, "confirmed");
        if (isHoldExpiredAt(now)) {
            throw new HoldExpiredException();
        }
        status = BookingStatus.CONFIRMED;
        expiresAt = null;
    }

    public void cancel() {
        requireStatus(BookingStatus.CONFIRMED, "cancelled");
        release(BookingStatus.CANCELLED);
    }

    private void release(BookingStatus finalStatus) {
        status = finalStatus;
        activeSlotKey = null;
    }

    private void requireStatus(BookingStatus expected, String action) {
        if (status != expected) {
            throw new BookingStateConflictException(
                    "Booking is " + status + " and cannot be " + action + "; it must be " + expected);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getPatientName() {
        return patientName;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getActiveSlotKey() {
        return activeSlotKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getVersion() {
        return version;
    }
}
