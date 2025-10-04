package com.msvcbilling.services.impl;

import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.paymentmethod.PaymentMethodClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.paymentmethod.PaymentMethod;
import com.msvcbilling.dtos.*;
import com.msvcbilling.entities.PaymentEntity;
import com.msvcbilling.mappers.PaymentMapper;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final PaymentMethodClient paymentMethodClient;
    private final PaymentMapper paymentMapper;

    @Transactional
    @Override
    public PaymentResponse processDirectPayment(DirectPaymentRequest request) throws Exception {
        log.info("Procesando pago directo para referencia: {}", request.externalReference());

        // Verificar idempotencia
        Optional<PaymentEntity> existing = paymentRepository.findByExternalReference(request.externalReference());
        if (existing.isPresent()) {
            PaymentEntity existingPayment = existing.get();
            log.info("Pago ya existe, retornando existente: {}", existingPayment.getPaymentId());
            return paymentMapper.entityToResponse(existingPayment);
        }

        IdentificationRequest identification = IdentificationRequest.builder()
                .type(request.identificationType())
                .number(request.identificationNumber())
                .build();

        PaymentPayerRequest payer = PaymentPayerRequest.builder()
                .email(request.payerEmail())
                .firstName(request.payerFirstName())
                .lastName(request.payerLastName())
                .identification(identification)
                .build();

        PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                .transactionAmount(request.amount())
                .token(request.token()) // ✅ Token viene del frontend
                .description(request.description() != null ? request.description() : "Pago FitDesk")
                .installments(request.installments())
                .paymentMethodId(request.paymentMethodId())
                .externalReference(request.externalReference())
                .payer(payer)
                .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-idempotency-key", UUID.randomUUID().toString());
        MPRequestOptions options = MPRequestOptions.builder()
                .customHeaders(headers)
                .build();

        Payment payment = paymentClient.create(paymentRequest, options);

        log.info("Pago creado en Mercado Pago. ID: {}, Status: {}", payment.getId(), payment.getStatus());

        PaymentEntity paymentEntity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .externalReference(request.externalReference())
                .paymentId(payment.getId())
                .token(request.token())
                .paymentMethodId(payment.getPaymentMethodId())
                .paymentTypeId(payment.getPaymentTypeId())
                .installments(payment.getInstallments())
                .authorizationCode(payment.getAuthorizationCode())
                .transactionId(payment.getId().toString())
                .amount(payment.getTransactionAmount())
                .currencyId(payment.getCurrencyId())
                .status(payment.getStatus())
                .statusDetail(payment.getStatusDetail())
                .payerEmail(request.payerEmail())
                .payerFirstName(request.payerFirstName())
                .payerLastName(request.payerLastName())
                .payerIdentificationType(request.identificationType())
                .payerIdentificationNumber(request.identificationNumber())
                .dateCreated(payment.getDateCreated() != null ?
                        OffsetDateTime.ofInstant(payment.getDateCreated().toInstant(), ZoneOffset.UTC) : OffsetDateTime.now())
                .dateApproved(payment.getDateApproved() != null ?
                        OffsetDateTime.ofInstant(payment.getDateApproved().toInstant(), ZoneOffset.UTC) : null)
                .build();

        paymentRepository.save(paymentEntity);

        return paymentMapper.entityToResponse(paymentEntity);
    }

    @Transactional
    @Override
    public PaymentResponse getPaymentStatus(String externalReference) {
        log.info("Consultando estado de pago para referencia: {}", externalReference);

        Optional<PaymentEntity> paymentOpt = paymentRepository.findByExternalReference(externalReference);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException("Pago no encontrado para la referencia: " + externalReference);
        }

        PaymentEntity paymentEntity = paymentOpt.get();

        if (paymentEntity.getPaymentId() != null) {
            try {
                Payment mpPayment = paymentClient.get(paymentEntity.getPaymentId());
                if (mpPayment != null && !Objects.equals(paymentEntity.getStatus(), mpPayment.getStatus())) {
                    updatePaymentFromMpPayment(mpPayment);
                    paymentEntity = paymentRepository.findByExternalReference(externalReference).orElse(paymentEntity);
                }
            } catch (
                    Exception e) {
                log.warn("Error consultando estado en Mercado Pago: {}", e.getMessage());
            }
        }

        return paymentMapper.entityToResponse(paymentEntity);
    }

    @Override
    public List<String> getPaymentMethods() {
        log.info("Consultando métodos de pago disponibles");

        try {
            var response = paymentMethodClient.list();
            if (response != null && response.getResults() != null) {
                return response.getResults().stream()
                        .filter(pm -> "credit_card".equals(pm.getPaymentTypeId()) ||
                                "debit_card".equals(pm.getPaymentTypeId()))
                        .map(PaymentMethod::getId)
                        .collect(Collectors.toList());
            }
        } catch (
                Exception e) {
            log.warn("Error consultando métodos de pago: {}", e.getMessage());
        }

        return Arrays.asList("visa", "master", "amex");
    }


    @Override
    public void updatePaymentFromMpPayment(Payment payment) {
        if (payment == null)
            return;

        log.info("Actualizando pago desde webhook. Payment ID: {}", payment.getId());

        String extRef = payment.getExternalReference();
        String status = payment.getStatus() != null ? payment.getStatus() : "unknown";

        Optional<PaymentEntity> localOpt = Optional.empty();

        if (payment.getId() != null) {
            localOpt = paymentRepository.findByPaymentId(payment.getId());
        }

        if (localOpt.isEmpty() && extRef != null) {
            localOpt = paymentRepository.findByExternalReference(extRef);
        }

        if (localOpt.isPresent()) {
            PaymentEntity local = localOpt.get();
            local.setStatus(status);
            local.setStatusDetail(payment.getStatusDetail());
            local.setAuthorizationCode(payment.getAuthorizationCode());

            if (payment.getDateApproved() != null && local.getDateApproved() == null) {
                local.setDateApproved(OffsetDateTime.ofInstant(payment.getDateApproved().toInstant(), ZoneOffset.UTC));
            }

            paymentRepository.save(local);
            log.info("Pago actualizado exitosamente. Nuevo estado: {}", status);
        } else {
            log.warn("No se encontró pago local para Payment ID: {} y External Reference: {}",
                    payment.getId(), extRef);
        }
    }
}