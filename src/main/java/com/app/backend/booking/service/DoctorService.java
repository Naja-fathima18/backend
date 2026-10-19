package com.app.backend.booking.service;

import com.app.backend.booking.config.BookingProperties;
import com.app.backend.booking.domain.Slot;
import com.app.backend.booking.domain.SlotSchedule;
import com.app.backend.booking.persistence.BookingSlotRepository;
import com.app.backend.booking.persistence.DoctorRepository;
import com.app.backend.common.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final BookingSlotRepository slotRepository;
    private final SlotSchedule schedule;
    private final Clock clock;

    public DoctorService(
            DoctorRepository doctorRepository,
            BookingSlotRepository slotRepository,
            BookingProperties properties,
            Clock clock) {
        this.doctorRepository = doctorRepository;
        this.slotRepository = slotRepository;
        this.schedule = new SlotSchedule(properties.zone(), properties.openingTime(), properties.closingTime());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DoctorView> list(Pageable pageable) {
        PageRequest byId = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        return doctorRepository.findAll(byId).map(DoctorView::of);
    }

    @Transactional(readOnly = true)
    public List<Slot> availableSlots(Long doctorId, LocalDate date) {
        requireDoctorExists(doctorId);
        Instant now = clock.instant();
        Set<Instant> taken = new HashSet<>(slotRepository.findTakenStartTimes(
                doctorId, schedule.startOfDay(date), schedule.startOfNextDay(date), now));
        return schedule.slotsOn(date).stream()
                .filter(slot -> !slot.startTime().isBefore(now))
                .filter(slot -> !taken.contains(slot.startTime()))
                .toList();
    }

    void requireDoctorExists(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new NotFoundException("Doctor " + doctorId + " not found");
        }
    }
}
