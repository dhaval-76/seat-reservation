package com.booking.service.strategy;

import com.booking.entity.Seat;
import java.util.List;

public record ReservationResult(
    List<Seat> reservedSeats,
    List<String> unavailableLabels
) {
    public boolean hasReservedSeats() {
        return reservedSeats != null && !reservedSeats.isEmpty();
    }
}
