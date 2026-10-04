package com.booking.service.strategy;

import com.booking.entity.Seat;
import java.util.List;

public interface ReservationStrategy {
    ReservationResult tryReserve(List<Seat> lockedSeats, List<String> requestedLabels, String userId);
}
