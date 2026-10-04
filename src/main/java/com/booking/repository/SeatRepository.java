package com.booking.repository;

import com.booking.entity.Seat;
import com.booking.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.label IN :labels ORDER BY s.label ASC")
    List<Seat> findByShowIdAndLabelIn(@Param("showId") Long showId, @Param("labels") List<String> labels);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.label IN :labels ORDER BY s.label ASC")
    List<Seat> findByShowIdAndLabelsForUpdate(@Param("showId") Long showId, @Param("labels") List<String> labels);

    List<Seat> findByShowId(Long showId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = :status")
    long countByShowIdAndStatus(@Param("showId") Long showId, @Param("status") SeatStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.heldBy = :userId AND s.status IN (com.booking.entity.SeatStatus.HELD, com.booking.entity.SeatStatus.CONFIRMED) ORDER BY s.label ASC")
    List<Seat> findReservedByUserForUpdate(@Param("showId") Long showId, @Param("userId") String userId);

    long countByStatus(SeatStatus status);
}
