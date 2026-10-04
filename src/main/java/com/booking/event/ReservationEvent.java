package com.booking.event;

public sealed interface ReservationEvent
        permits ReservationCreatedEvent, ReservationCancelledEvent,
                ReservationExpiredEvent, ReservationDeclinedEvent {
}
