package com.msvcbilling.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long paymentId,
        String status,
        String statusDetail,
        BigDecimal transactionAmount,
        String currencyId,
        String externalReference,
        String paymentMethodId,
        String paymentTypeId,
        OffsetDateTime dateCreated,
        OffsetDateTime dateApproved,
        String authorizationCode,
        String transactionId
) {}