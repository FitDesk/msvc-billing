package com.msvcbilling.dtos.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentMethodRequest(
    @NotBlank(message = "El token de la tarjeta es requerido") String cardToken,

    @NotBlank(message = "El email del pagador es requerido") String payerEmail,

    @NotBlank(message = "El número de tarjeta es requerido para validación")
    String cardNumber,

    @NotBlank(message = "El nombre del titular es requerido")
    String cardHolderName,

    @NotNull(message = "El mes de expiración es requerido")
    @Min(1)
    @Max(12)
    Integer expirationMonth,

    @NotNull(message = "El año de expiración es requerido")
    @Min(2024)
    Integer expirationYear,
    @NotBlank(message = "La marca de la tarjeta es requerida") String cardBrand,
    String nickname,

    @NotNull(message = "Debe indicar si será la tarjeta por defecto")
    Boolean setAsDefault,

    @NotBlank(message = "El tipo de documento es requerido")
    String identificationType,

    @NotBlank(message = "El número de documento es requerido")
    String identificationNumber
) {}
