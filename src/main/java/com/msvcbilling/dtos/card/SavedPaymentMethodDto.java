package com.msvcbilling.dtos.card;

import java.util.UUID;

public record SavedPaymentMethodDto(
        UUID id,
        String lastFourDigits,
        String cardToken,
        String cardHolderName,
        String cardBrand,
        String cardType,
        Integer expirationMonth,
        Integer expirationYear,
        Boolean isDefault,
        String nickname,
        Boolean isExpired,
        String displayName
) {}