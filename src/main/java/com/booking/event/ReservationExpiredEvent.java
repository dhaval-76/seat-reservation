package com.booking.event;

public record ReservationExpiredEvent(
        Long reservationId,
        String userId
) implements ReservationEvent {
}
