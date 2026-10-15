package com.app.backend.parking.api;

import com.app.backend.parking.domain.VisitFee;
import com.app.backend.parking.service.ParkingFeeService;
import java.time.LocalTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking")
public class ParkingFeeController {

    private final ParkingFeeService parkingFeeService;

    public ParkingFeeController(ParkingFeeService parkingFeeService) {
        this.parkingFeeService = parkingFeeService;
    }

    @GetMapping("/fee")
    public VisitFeeResponse visitFee(@RequestParam String entry, @RequestParam String exit) {
        LocalTime entryTime = ClockTime.parse("entry", entry);
        LocalTime exitTime = ClockTime.parse("exit", exit);
        VisitFee visitFee = parkingFeeService.visitFee(entryTime, exitTime);
        return new VisitFeeResponse(
                ClockTime.format(entryTime), ClockTime.format(exitTime), visitFee.durationMinutes(), visitFee.fee());
    }
}
