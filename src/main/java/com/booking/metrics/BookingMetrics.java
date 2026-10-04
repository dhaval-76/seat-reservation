package com.booking.metrics;

import com.booking.entity.SeatStatus;
import com.booking.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {

    private final Counter confirmedCounter;
    private final Counter declinedSeatUnavailableCounter;
    private final Counter declinedUserLimitCounter;
    private final Counter declinedIdempotentReplayCounter;
    private final Timer reservationDurationTimer;

    public BookingMetrics(MeterRegistry registry, SeatRepository seatRepository) {
        this.confirmedCounter = Counter.builder("booking.reservations.confirmed")
                .description("Total confirmed reservations")
                .register(registry);

        this.declinedSeatUnavailableCounter = Counter.builder("booking.reservations.declined")
                .tag("reason", "seat_unavailable")
                .description("Total declined reservations")
                .register(registry);

        this.declinedUserLimitCounter = Counter.builder("booking.reservations.declined")
                .tag("reason", "user_limit")
                .description("Total declined reservations")
                .register(registry);

        this.declinedIdempotentReplayCounter = Counter.builder("booking.reservations.declined")
                .tag("reason", "idempotent_replay")
                .description("Idempotent replay returns (no new reservation created)")
                .register(registry);

        this.reservationDurationTimer = Timer.builder("booking.reservation.duration.seconds")
                .description("Time taken to process a reservation")
                .register(registry);

        Gauge.builder("booking.seats.available", seatRepository,
                repo -> (double) repo.countByStatus(SeatStatus.AVAILABLE))
                .description("Total available seats across all shows")
                .register(registry);
    }

    public void recordConfirmed() {
        confirmedCounter.increment();
    }

    public void recordDeclined(String reason) {
        switch (reason) {
            case "seat_unavailable" -> declinedSeatUnavailableCounter.increment();
            case "user_limit" -> declinedUserLimitCounter.increment();
            case "idempotent_replay" -> declinedIdempotentReplayCounter.increment();
        }
    }

    public Timer getReservationDurationTimer() {
        return reservationDurationTimer;
    }
}
