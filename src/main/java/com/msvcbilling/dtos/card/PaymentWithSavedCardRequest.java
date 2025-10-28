package com.msvcbilling.dtos.card;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentWithSavedCardRequest(
        @NotNull(message = "El ID de la tarjeta guardada es requerido")
        UUID savedCardId,

        @NotNull(message = "El ID del usuario es requerido")
        UUID userId,

        @NotNull(message = "El ID del plan es requerido")
        UUID planId,

        @NotNull(message = "El monto es requerido")
        BigDecimal amount,
        @NotBlank(message = "El email del pagador es requerido")
        @Email
        String payerEmail,

        @NotBlank(message = "El nombre del pagador es requerido")
        String payerName,
        String description,

        Integer installments,

        @NotNull(message = "El CVV es requerido por seguridad")
        String cvv
) {
}