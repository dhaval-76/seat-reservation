package com.booking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "booking")
@Validated
public record BookingProperties(
        @NotBlank String reservationStrategy,
        @Min(1) long holdDurationSeconds,
        @Min(1000) long holdCleanupIntervalMs,
        @Min(1) long maxSeatsPerUserPerShow
) {
    public BookingProperties {
        if (reservationStrategy == null) reservationStrategy = "all-or-nothing";
        if (holdDurationSeconds == 0) holdDurationSeconds = 300;
        if (holdCleanupIntervalMs == 0) holdCleanupIntervalMs = 30000;
        if (maxSeatsPerUserPerShow == 0) maxSeatsPerUserPerShow = 10;
    }
}
