package com.booking.controller;

import com.booking.dto.ReservationResponse;
import com.booking.dto.ReserveRequest;
import com.booking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable Long showId,
            @Valid @RequestBody ReserveRequest request,
            Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(showId, userId, request));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable Long id,
            Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(reservationService.cancel(id, userId));
    }
}
