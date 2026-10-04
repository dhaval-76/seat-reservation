package com.booking.event;

import com.booking.service.ShowServiceInterface;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ShowCacheEvictionListener {

    private final ShowServiceInterface showService;

    public ShowCacheEvictionListener(ShowServiceInterface showService) {
        this.showService = showService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        showService.evictShow(event.showId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelledEvent event) {
        showService.evictShow(event.showId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationExpired(ReservationExpiredEvent event) {
        showService.evictShow(event.showId());
    }
}
