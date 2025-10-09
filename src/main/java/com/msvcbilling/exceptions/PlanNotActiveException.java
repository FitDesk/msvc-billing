package com.msvcbilling.exceptions;

public class PlanNotActiveException extends RuntimeException {
    public PlanNotActiveException(String message) {
        super(message);
    }
}
