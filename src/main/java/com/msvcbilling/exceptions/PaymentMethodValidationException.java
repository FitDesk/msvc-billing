package com.msvcbilling.exceptions;

public class PaymentMethodValidationException extends RuntimeException {
    public PaymentMethodValidationException(String message) {
        super(message);
    }
}