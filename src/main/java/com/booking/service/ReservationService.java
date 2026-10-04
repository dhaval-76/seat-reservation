package com.booking.service;

import com.booking.config.BookingProperties;
import com.booking.dto.ReservationResponse;
import com.booking.dto.ReserveRequest;
import com.booking.entity.*;
import com.booking.event.*;
import com.booking.exception.*;
import com.booking.metrics.BookingMetrics;
import com.booking.repository.ReservationRepository;
import com.booking.repository.SeatRepository;
import com.booking.repository.ShowRepository;
import com.booking.service.strategy.ReservationResult;
import com.booking.service.strategy.ReservationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationService implements ReservationServiceInterface {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStrategy strategy;
    private final BookingMetrics metrics;
    private final BookingProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationStrategy strategy,
            BookingMetrics metrics,
            BookingProperties properties,
            ApplicationEventPublisher eventPublisher) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.strategy = strategy;
        this.metrics = metrics;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ReservationResponse reserve(Long showId, String userId, ReserveRequest request) {
        return metrics.getReservationDurationTimer().record(() -> doReserve(showId, userId, request));
    }

    private ReservationResponse doReserve(Long showId, String userId, ReserveRequest request) {
        // Verify show exists
        showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        // Idempotency check
        Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            // Same key, same show, same user → return existing
            if (r.getShowId().equals(showId) && r.getUserId().equals(userId)) {
                List<String> existingLabels = Arrays.asList(r.getSeatLabels().split(","));
                List<String> requestedLabels = request.seats().stream().sorted().toList();
                if (existingLabels.equals(requestedLabels)) {
                    return toResponse(r);
                }
            }
            throw new IdempotencyConflictException(request.idempotencyKey());
        }

        // Sort requested labels for deterministic locking
        List<String> sortedLabels = request.seats().stream().sorted().toList();

        // Lock seats with SELECT FOR UPDATE (ordered by label to prevent deadlocks)
        List<Seat> lockedSeats = seatRepository.findByShowIdAndLabelsForUpdate(showId, sortedLabels);

        // Verify all requested seats exist
        if (lockedSeats.size() != sortedLabels.size()) {
            List<String> foundLabels = lockedSeats.stream().map(Seat::getLabel).toList();
            List<String> missing = sortedLabels.stream().filter(l -> !foundLabels.contains(l)).toList();
            throw new SeatNotFoundException(missing);
        }

        // Per-user limit check
        long currentCount = reservationRepository.countActiveSeatsByShowIdAndUserId(showId, userId);
        if (currentCount + sortedLabels.size() > properties.maxSeatsPerUserPerShow()) {
            metrics.recordDeclined("user_limit");
            eventPublisher.publishEvent(new ReservationDeclinedEvent("user_limit", showId, userId));
            throw new UserLimitExceededException(userId, currentCount, properties.maxSeatsPerUserPerShow());
        }

        // Apply strategy (all-or-nothing or best-effort)
        ReservationResult result;
        try {
            result = strategy.tryReserve(lockedSeats, sortedLabels, userId);
        } catch (SeatUnavailableException e) {
            metrics.recordDeclined("seat_unavailable");
            eventPublisher.publishEvent(new ReservationDeclinedEvent("seat_unavailable", showId, userId));
            throw e;
        }

        // Flush seat changes
        seatRepository.saveAll(result.reservedSeats());

        // Create reservation record
        Reservation reservation = new Reservation();
        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(request.idempotencyKey());
        reservation.setStatus(ReservationStatus.HELD);
        reservation.setSeatLabels(result.reservedSeats().stream()
                .map(Seat::getLabel).sorted().reduce((a, b) -> a + "," + b).orElse(""));
        reservation.setAmountPaise(request.amountPaise());
        reservation.setExpiresAt(Instant.now().plus(properties.holdDurationSeconds(), ChronoUnit.SECONDS));
        reservation = reservationRepository.save(reservation);

        metrics.recordConfirmed();
        eventPublisher.publishEvent(new ReservationCreatedEvent(
                reservation.getId(), showId, userId, reservation.getSeatLabels()));

        return toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse cancel(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.HELD &&
            reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ReservationNotCancellableException(reservationId, reservation.getStatus().name());
        }

        // Release seats
        List<String> labels = Arrays.asList(reservation.getSeatLabels().split(","));
        List<Seat> seats = seatRepository.findByShowIdAndLabelsForUpdate(reservation.getShowId(), labels);
        seats.stream()
                .filter(s -> userId.equals(s.getHeldBy()))
                .forEach(Seat::release);
        seatRepository.saveAll(seats);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        eventPublisher.publishEvent(new ReservationCancelledEvent(reservationId, userId));

        return toResponse(reservation);
    }

    @Override
    @Transactional
    public int expireHolds() {
        List<Reservation> expired = reservationRepository.findExpiredHolds(Instant.now());
        int count = 0;
        for (Reservation reservation : expired) {
            List<String> labels = Arrays.asList(reservation.getSeatLabels().split(","));
            List<Seat> seats = seatRepository.findByShowIdAndLabelsForUpdate(reservation.getShowId(), labels);
            seats.stream()
                    .filter(s -> reservation.getUserId().equals(s.getHeldBy()) && s.getStatus() == SeatStatus.HELD)
                    .forEach(Seat::release);
            seatRepository.saveAll(seats);

            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);

            eventPublisher.publishEvent(new ReservationExpiredEvent(
                    reservation.getId(), reservation.getUserId()));

            count++;
        }
        return count;
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getShowId(),
                r.getUserId(),
                r.getStatus().name(),
                Arrays.asList(r.getSeatLabels().split(",")),
                r.getAmountPaise(),
                r.getCreatedAt(),
                r.getExpiresAt()
        );
    }
}
