package com.booking.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ShowResponse(
    Long id,
    String name,
    long pricePaise,
    List<SeatInfo> seats,
    Map<String, Long> summary
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SeatInfo(String label, String status) {}
}
