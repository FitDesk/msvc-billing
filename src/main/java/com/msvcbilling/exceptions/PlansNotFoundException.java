package com.msvcbilling.exceptions;

public class PlansNotFoundException extends RuntimeException {
    public PlansNotFoundException(String message) {
        super(message);
    }
}
