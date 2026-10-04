package com.booking.event;

public record ReservationCancelledEvent(
        Long reservationId,
        Long showId,
        String userId
) implements ReservationEvent {
}
