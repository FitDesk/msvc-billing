package com.msvcbilling.dtos.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlanUpgradeRequestDto(
        @NotNull(message = "El ID del usuario es requerido")
        UUID userId,

        @NotNull(message = "El ID del nuevo plan es requerido")
        UUID newPlanId,

        @NotBlank(message = "El token de la tarjeta es requerido")
        String token,

        @NotNull(message = "Las cuotas son requeridas")
        Integer installments,

        @NotBlank(message = "El método de pago es requerido")
        String paymentMethodId,

        @NotBlank(message = "El tipo de documento es requerido")
        String identificationType,

        @NotBlank(message = "El número de documento es requerido")
        String identificationNumber
) {
}