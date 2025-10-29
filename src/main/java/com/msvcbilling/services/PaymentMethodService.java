package com.msvcbilling.services;

import com.msvcbilling.dtos.card.CreatePaymentMethodRequest;
import com.msvcbilling.dtos.card.PaymentWithSavedCardRequest;
import com.msvcbilling.dtos.card.SavedPaymentMethodDto;
import com.msvcbilling.dtos.card.UpdatePaymentMethodRequest;
import com.msvcbilling.dtos.payment.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentMethodService {
    SavedPaymentMethodDto savePaymentMethod(UUID userId, CreatePaymentMethodRequest request);

    List<SavedPaymentMethodDto> getUserPaymentMethods(UUID userId);

    SavedPaymentMethodDto getPaymentMethod(UUID userId, UUID cardId);

    SavedPaymentMethodDto updatePaymentMethod(UUID userId, UUID cardId, UpdatePaymentMethodRequest request);

    void deletePaymentMethod(UUID userId, UUID cardId);

    PaymentResponse processPaymentWithSavedCard(PaymentWithSavedCardRequest request) throws Exception;
}