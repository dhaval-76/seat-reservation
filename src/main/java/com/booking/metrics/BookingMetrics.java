package com.booking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {

    private final Counter confirmedCounter;
    private final Counter declinedSeatUnavailableCounter;
    private final Counter declinedUserLimitCounter;
    private final Timer reservationDurationTimer;

    public BookingMetrics(MeterRegistry registry) {
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

        this.reservationDurationTimer = Timer.builder("booking.reservation.duration.seconds")
                .description("Time taken to process a reservation")
                .register(registry);
    }

    public void recordConfirmed() {
        confirmedCounter.increment();
    }

    public void recordDeclined(String reason) {
        switch (reason) {
            case "seat_unavailable" -> declinedSeatUnavailableCounter.increment();
            case "user_limit" -> declinedUserLimitCounter.increment();
        }
    }

    public Timer getReservationDurationTimer() {
        return reservationDurationTimer;
    }
}
