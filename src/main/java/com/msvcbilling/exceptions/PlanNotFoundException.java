package com.msvcbilling.exceptions;

import java.util.UUID;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(UUID planId) {
        super("No se pudo encontrar la membresia con id " + planId);
    }
}
