package com.booking.event;

public record ReservationCreatedEvent(
        Long reservationId,
        Long showId,
        String userId,
        String seatLabels
) implements ReservationEvent {
}
