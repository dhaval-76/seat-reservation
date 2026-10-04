package com.booking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);

    @Async
    @EventListener
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("[Event:] Reservation {} created for user {} on show {} seats {}",
                event.reservationId(), event.userId(), event.showId(), event.seatLabels());
    }

    @Async
    @EventListener
    public void onReservationCancelled(ReservationCancelledEvent event) {
        log.info("[Event:] Reservation {} cancelled by user {}",
                event.reservationId(), event.userId());
    }

    @Async
    @EventListener
    public void onReservationExpired(ReservationExpiredEvent event) {
        log.info("[Event:] Reservation {} expired for user {}",
                event.reservationId(), event.userId());
    }

    @Async
    @EventListener
    public void onReservationDeclined(ReservationDeclinedEvent event) {
        log.info("[Event:] Reservation declined for user {} on show {} reason: {}",
                event.userId(), event.showId(), event.reason());
    }
}
