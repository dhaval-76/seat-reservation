package com.booking.repository;

import com.booking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("SELECT DISTINCT s FROM Show s LEFT JOIN FETCH s.seats WHERE s.id = :id")
    Optional<Show> findByIdWithSeats(@Param("id") Long id);
}
