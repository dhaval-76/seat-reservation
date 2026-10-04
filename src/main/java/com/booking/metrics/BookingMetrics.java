package com.booking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {

    private final Counter confirmedCounter;
    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.confirmedCounter = Counter.builder("booking.reservations.confirmed")
                .description("Total confirmed reservations")
                .register(registry);
    }

    public void recordConfirmed() {
        confirmedCounter.increment();
    }

    public void recordDeclined(String reason) {
        Counter.builder("booking.reservations.declined")
                .tag("reason", reason)
                .description("Total declined reservations")
                .register(registry)
                .increment();
    }
}
