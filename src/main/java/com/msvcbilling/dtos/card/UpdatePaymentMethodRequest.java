package com.msvcbilling.dtos.card;

public record UpdatePaymentMethodRequest(
        String nickname,
        Boolean setAsDefault
) {}