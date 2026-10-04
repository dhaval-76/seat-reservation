package com.booking.dto;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
    Long id,
    Long showId,
    String userId,
    String status,
    List<String> seats,
    long amountPaise,
    Instant createdAt,
    Instant expiresAt
) {}
