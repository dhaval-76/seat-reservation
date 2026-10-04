package com.booking.event;

public record ReservationDeclinedEvent(
        String reason,
        Long showId,
        String userId
) implements ReservationEvent {
}
