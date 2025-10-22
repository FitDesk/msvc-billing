package com.msvcbilling.dtos.statistics;

public record StatusDistributionDto(
        String status,
        long total
) {
}
