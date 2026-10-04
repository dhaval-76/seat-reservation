package com.booking.repository;

import com.booking.entity.Reservation;
import com.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "SELECT COUNT(*) FROM seats WHERE show_id = :showId AND held_by = :userId AND status IN ('HELD', 'CONFIRMED')", nativeQuery = true)
    long countActiveSeatsByShowIdAndUserId(@Param("showId") Long showId, @Param("userId") String userId);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'HELD' AND r.expiresAt < :now")
    List<Reservation> findExpiredHolds(@Param("now") Instant now);

    Optional<Reservation> findByIdAndUserId(Long id, String userId);
}
