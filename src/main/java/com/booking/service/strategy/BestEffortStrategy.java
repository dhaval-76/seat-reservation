package com.booking.service.strategy;

import com.booking.entity.Seat;
import com.booking.entity.SeatStatus;
import com.booking.exception.SeatUnavailableException;

import org.springframework.stereotype.Component;

import java.util.List;

@Component("bestEffortStrategy")
public class BestEffortStrategy implements ReservationStrategy {

    @Override
    public ReservationResult tryReserve(List<Seat> lockedSeats, List<String> requestedLabels, String userId) {
        List<Seat> available = lockedSeats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .toList();

        List<String> unavailable = lockedSeats.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(Seat::getLabel)
                .toList();

        if (available.isEmpty()) {
            throw new SeatUnavailableException(requestedLabels);
        }

        available.forEach(s -> s.reserve(userId));
        return new ReservationResult(available, unavailable);
    }
}
