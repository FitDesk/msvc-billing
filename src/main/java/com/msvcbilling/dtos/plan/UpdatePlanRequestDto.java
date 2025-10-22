package com.msvcbilling.dtos.plan;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePlanRequestDto(
        String name,
        String description,
        BigDecimal price,
        Integer durationMonths,
        String currency,
        Boolean isActive,
        Boolean isPopular,
        List<String> features
) {
}
