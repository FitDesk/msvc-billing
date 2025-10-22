package com.msvcbilling.dtos.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentDetailsResponseDto(
        UUID id,
        String payerEmail,
        String paymentMethodId,
        String planName,
        BigDecimal amount,
        String planExpirationDate,
        String paymentDate,
        String status
) {
}
