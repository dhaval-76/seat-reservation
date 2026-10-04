package com.booking.event;

public record ReservationExpiredEvent(
        Long reservationId,
        Long showId,
        String userId
) implements ReservationEvent {
}
