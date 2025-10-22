package com.msvcbilling.dtos.statistics;

public record StatisticDataDto<T>(
        T currenValue,
        double percentageChange,
        String trend
) {
}
