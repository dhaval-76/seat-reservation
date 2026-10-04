package com.booking.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
