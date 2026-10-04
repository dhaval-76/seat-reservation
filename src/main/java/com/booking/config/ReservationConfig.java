package com.booking.config;

import com.booking.service.strategy.ReservationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
public class ReservationConfig {

    private static final Map<String, String> STRATEGY_NAME_MAP = Map.of(
            "all-or-nothing", "allOrNothingStrategy",
            "best-effort", "bestEffortStrategy"
    );

    @Bean
    @Primary
    public ReservationStrategy reservationStrategy(
            BookingProperties properties,
            Map<String, ReservationStrategy> strategies) {
        String beanName = STRATEGY_NAME_MAP.getOrDefault(
                properties.reservationStrategy(), "allOrNothingStrategy");
        return strategies.get(beanName);
    }
}
