package com.msvcbilling.services;

import com.mercadopago.resources.payment.Payment;
import com.msvcbilling.dtos.payment.DirectPaymentRequest;
import com.msvcbilling.dtos.payment.PaymentDetailsResponseDto;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.dtos.payment.PlanUpgradeRequestDto;
import com.msvcbilling.dtos.statistics.DashboardStatisticsResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentService {
    PaymentResponse processDirectPayment(DirectPaymentRequest request) throws Exception;
    PaymentResponse getPaymentStatus(String externalReference) throws Exception;
    List<String> getPaymentMethods() throws Exception;
    void updatePaymentFromMpPayment(Payment payment);
    void simulatePaymentApproval(String externalReference, String authorizationCode);
    Page<PaymentDetailsResponseDto> getAllPaymentsDetails(Pageable pageable, String status, String paymentMethodId, OffsetDateTime startDate, OffsetDateTime endDate);
    DashboardStatisticsResponseDto getDashboardStatistics();
    PaymentResponse processPlanUpgrade(PlanUpgradeRequestDto request) throws Exception;
}
