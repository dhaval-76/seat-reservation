package com.booking.dto;

import java.util.List;
import java.util.Map;

public record ShowResponse(
    Long id,
    String name,
    List<SeatInfo> seats,
    Map<String, Long> summary
) {
    public record SeatInfo(String label, String status) {}
}
