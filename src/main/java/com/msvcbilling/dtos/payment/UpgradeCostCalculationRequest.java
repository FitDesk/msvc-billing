package com.msvcbilling.dtos.payment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpgradeCostCalculationRequest(
        @NotNull(message = "El ID del usuario es requerido")
        UUID userId,
        @NotNull(message = "El ID del nuevo plan es requerido")
        UUID newPlanId
) {}