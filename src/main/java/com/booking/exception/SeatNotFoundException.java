package com.booking.exception;

import java.util.List;

public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(List<String> labels) {
        super("Seats not found: " + labels);
    }
}
