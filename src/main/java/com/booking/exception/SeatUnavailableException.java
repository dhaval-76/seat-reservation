package com.booking.exception;

import java.util.List;

public class SeatUnavailableException extends RuntimeException {
    private final List<String> unavailableSeats;

    public SeatUnavailableException(List<String> unavailableSeats) {
        super("Seats unavailable: " + unavailableSeats);
        this.unavailableSeats = unavailableSeats;
    }

    public List<String> getUnavailableSeats() { return unavailableSeats; }
}
