package com.booking.config;

import com.booking.service.strategy.AllOrNothingStrategy;
import com.booking.service.strategy.BestEffortStrategy;
import com.booking.service.strategy.ReservationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationConfig {

    @Bean
    public ReservationStrategy reservationStrategy(
            @Value("${booking.reservation-strategy:all-or-nothing}") String strategy) {
        return switch (strategy) {
            case "best-effort" -> new BestEffortStrategy();
            default -> new AllOrNothingStrategy();
        };
    }
}
