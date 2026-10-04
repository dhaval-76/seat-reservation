package com.booking.exception;

public class ReservationNotCancellableException extends RuntimeException {
    public ReservationNotCancellableException(Long id, String status) {
        super("Reservation " + id + " cannot be cancelled, current status: " + status);
    }
}
