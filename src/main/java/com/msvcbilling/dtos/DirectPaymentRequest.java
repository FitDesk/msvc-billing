package com.msvcbilling.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DirectPaymentRequest(
        @NotNull(message = "La referencia externa es requerida")
        String externalReference,

        @NotNull(message = "El monto es requerido")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        BigDecimal amount,

        @NotBlank(message = "El email del pagador es requerido")
        @Email(message = "Email inválido")
        String payerEmail,

        @NotBlank(message = "El nombre del pagador es requerido")
        String payerFirstName,

        @NotBlank(message = "El apellido del pagador es requerido")
        String payerLastName,

        String description,

        @NotBlank(message = "El token de la tarjeta es requerido")
        String token,

        @NotNull(message = "Las cuotas son requeridas")
        Integer installments,

        @NotBlank(message = "El método de pago es requerido")
        String paymentMethodId,

        @NotBlank(message = "El tipo de documento es requerido")
        String identificationType,

        @NotBlank(message = "El número de documento es requerido")
        String identificationNumber
) {}