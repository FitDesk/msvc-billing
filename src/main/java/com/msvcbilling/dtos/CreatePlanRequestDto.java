package com.msvcbilling.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreatePlanRequestDto(
        @NotBlank(message = "El nombre del plan es requerido")
        String name,

        String description,

        @NotNull(message = "El precio es requerido")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
        BigDecimal price,

        @NotNull(message = "La duración es requerida")
        @Min(value = 1, message = "La duración debe ser al menos 1 mes")
        Integer durationMonths,

        String currency,
        Boolean isActive,
        Boolean isPopular,
        List<String> features
) {
}