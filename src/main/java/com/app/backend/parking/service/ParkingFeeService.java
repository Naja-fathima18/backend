package com.app.backend.parking.service;

import com.app.backend.parking.domain.ParkingTariff;
import com.app.backend.parking.domain.VisitFee;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

@Service
public class ParkingFeeService {

    public VisitFee visitFee(LocalTime entry, LocalTime exit) {
        int durationMinutes = ParkingTariff.durationMinutes(entry, exit);
        return new VisitFee(durationMinutes, ParkingTariff.feeForDuration(durationMinutes));
    }
}
