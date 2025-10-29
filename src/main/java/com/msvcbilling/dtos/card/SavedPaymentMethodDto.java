package com.msvcbilling.dtos.card;

import java.util.UUID;

public record SavedPaymentMethodDto(
        UUID id,
        String lastFourDigits,
        String cardToken, // <-- AÑADE ESTE CAMPO
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