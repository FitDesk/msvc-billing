package com.msvcbilling.services;

import com.mercadopago.resources.payment.Payment;
import com.msvcbilling.dtos.*;

import java.util.List;

public interface PaymentService {
    PaymentResponse processDirectPayment(DirectPaymentRequest request) throws Exception;
    PaymentResponse getPaymentStatus(String externalReference) throws Exception;
    List<String> getPaymentMethods() throws Exception;
    void updatePaymentFromMpPayment(Payment payment);
    void simulatePaymentApproval(String externalReference, String authorizationCode);
}
