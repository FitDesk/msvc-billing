package com.msvcbilling.dtos.statistics;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatisticsResponseDto(
        StatisticDataDto<BigDecimal> monthlyRevenue,
        StatisticDataDto<Long> newMembers,
        long totalApprovedPayments,
        List<PlanDistributionDto> topPlans,
        List<StatusDistributionDto> paymentStatusDistribution
) {
}
