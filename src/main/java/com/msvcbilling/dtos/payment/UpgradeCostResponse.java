package com.msvcbilling.dtos.payment;

import java.math.BigDecimal;

public record UpgradeCostResponse(
        BigDecimal originalPrice,

        BigDecimal upgradeCost,

        BigDecimal unusedCredit,

        Long daysRemaining,

        String newPlanName,
        String newPlanDescription
) {
}