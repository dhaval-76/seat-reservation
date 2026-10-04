package com.booking.service;

import com.booking.dto.ReservationResponse;
import com.booking.dto.ReserveRequest;

public interface ReservationServiceInterface {
    ReservationResponse reserve(Long showId, String userId, ReserveRequest request);
    ReservationResponse cancel(Long reservationId, String userId);
    int expireHolds();
}
