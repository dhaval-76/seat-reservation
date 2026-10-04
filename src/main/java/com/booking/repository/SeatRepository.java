package com.booking.repository;

import com.booking.entity.Seat;
import com.booking.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.label IN :labels ORDER BY s.label ASC")
    List<Seat> findByShowIdAndLabelIn(@Param("showId") Long showId, @Param("labels") List<String> labels);

    @Query(value = "SELECT s.* FROM seats s WHERE s.show_id = :showId AND s.label IN :labels ORDER BY s.label ASC FOR UPDATE",
           nativeQuery = true)
    List<Seat> findByShowIdAndLabelsForUpdate(@Param("showId") Long showId, @Param("labels") List<String> labels);

    List<Seat> findByShowId(Long showId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show.id = :showId AND s.status = :status")
    long countByShowIdAndStatus(@Param("showId") Long showId, @Param("status") SeatStatus status);

    @Query(value = "SELECT s.* FROM seats s WHERE s.show_id = :showId AND s.held_by = :userId AND s.status = 'HELD' ORDER BY s.label ASC FOR UPDATE",
           nativeQuery = true)
    List<Seat> findHeldByUserForUpdate(@Param("showId") Long showId, @Param("userId") String userId);
}
