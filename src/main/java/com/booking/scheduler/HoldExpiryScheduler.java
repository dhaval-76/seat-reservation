package com.booking.scheduler;

import com.booking.service.ReservationServiceInterface;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final ReservationServiceInterface reservationService;

    public HoldExpiryScheduler(ReservationServiceInterface reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${booking.hold-cleanup-interval-ms:30000}")
    @SchedulerLock(name = "releaseExpiredHolds", lockAtLeastFor = "PT4S", lockAtMostFor = "PT5M")
    public void releaseExpiredHolds() {
        int expired = reservationService.expireHolds();
        if (expired > 0) {
            log.info("Released {} expired holds", expired);
        }
    }
}
