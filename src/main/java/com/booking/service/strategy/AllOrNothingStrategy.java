package com.booking.service.strategy;

import com.booking.entity.Seat;
import com.booking.entity.SeatStatus;
import com.booking.exception.SeatUnavailableException;

import java.util.List;

public class AllOrNothingStrategy implements ReservationStrategy {

    @Override
    public ReservationResult tryReserve(List<Seat> lockedSeats, List<String> requestedLabels, String userId) {
        List<String> unavailable = lockedSeats.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(Seat::getLabel)
                .toList();

        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException(unavailable);
        }

        lockedSeats.forEach(s -> s.hold(userId));
        return new ReservationResult(lockedSeats, List.of());
    }
}
