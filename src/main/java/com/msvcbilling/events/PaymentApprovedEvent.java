package com.msvcbilling.events;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID paymentId,
        UUID userId,
//        String planId,
//        String planName,
        BigDecimal amount,
        String externalReference,
        OffsetDateTime paymentDate,
        String mercadoPagoTransactionId
) {}