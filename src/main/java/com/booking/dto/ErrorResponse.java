package com.booking.dto;

import java.time.Instant;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String requestId
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
