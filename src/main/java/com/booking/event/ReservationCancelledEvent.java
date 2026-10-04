package com.booking.event;

public record ReservationCancelledEvent(
        Long reservationId,
        String userId
) implements ReservationEvent {
}
