package com.booking.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReservationResponse(
    Long reservationId,
    Long showId,
    String userId,
    String status,
    List<String> seats,
    long amountPaise,
    Instant createdAt,
    Instant expiresAt
) {}
