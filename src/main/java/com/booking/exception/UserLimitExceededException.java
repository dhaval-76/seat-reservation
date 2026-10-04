package com.booking.exception;

public class UserLimitExceededException extends RuntimeException {
    public UserLimitExceededException(String userId, long current, long max) {
        super("User " + userId + " has " + current + " active reservations, max is " + max);
    }
}
