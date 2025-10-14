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
import com.msvcbilling.entities.PlanEntity;
import com.msvcbilling.events.PaymentApprovedEvent;
import com.msvcbilling.exceptions.PlanNotActiveException;
import com.msvcbilling.exceptions.PlanNotFoundException;
import com.msvcbilling.mappers.PaymentMapper;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.repository.PlanRepository;
import com.msvcbilling.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final PlanRepository planRepository;
    private final PaymentClient paymentClient;
    private final PaymentMethodClient paymentMethodClient;
    private final PaymentMapper paymentMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    @Override
    public PaymentResponse processDirectPayment(DirectPaymentRequest request) throws Exception {
        log.info("🔄 Procesando pago directo para referencia: {}", request.externalReference());

        PlanEntity plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new PlanNotFoundException("Plan no econtrado con ID " + request.planId()));

        if (!plan.getIsActive()) {
            throw new PlanNotActiveException("El plan selecionado no esta activo");
        }
        if (plan.getPrice().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("El monto no coincide con el precio del plan");
        }

        Optional<PaymentEntity> existing = paymentRepository.findByExternalReference(request.externalReference());
        if (existing.isPresent()) {
            PaymentEntity existingPayment = existing.get();
            log.info("♻️ Pago ya existe, retornando existente: {}", existingPayment.getPaymentId());
            return paymentMapper.entityToResponse(existingPayment);
        }

        try {
            // Log de datos que enviamos (sin datos sensibles)
            log.info("💳 Creando pago - Monto: {}, Email: {}, Método: {}",
                    request.amount(), request.payerEmail(), request.paymentMethodId());

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
                    .token(request.token())
                    .description(request.description() != null ? request.description() : "Pago FitDesk")
                    .installments(request.installments())
                    .paymentMethodId(request.paymentMethodId())
                    .externalReference(request.externalReference())
                    .payer(payer)
                    .statementDescriptor("FITDESK")
                    .binaryMode(false)
                    .build();

            Map<String, String> headers = new HashMap<>();
            headers.put("x-idempotency-key", UUID.randomUUID().toString());
            MPRequestOptions options = MPRequestOptions.builder()
                    .customHeaders(headers)
                    .build();

            log.info("Enviando request a Mercado Pago...");
            Payment payment = paymentClient.create(paymentRequest, options);


            log.info("✅ Pago creado en Mercado Pago. ID: {}, Status: {}, Detail: {}",
                    payment.getId(),
                    payment.getStatus(),
                    payment.getStatusDetail());
            String authCode = payment.getAuthorizationCode();
            if (authCode == null || authCode.isEmpty()) {
                authCode = "PENDING";
                log.warn("⏳ Pago en proceso. AuthCode será actualizado posteriormente");
            }
            PaymentEntity paymentEntity = PaymentEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(request.userId())
                    .plan(plan)
                    .externalReference(request.externalReference())
                    .paymentId(payment.getId())
                    .token(request.token())
                    .paymentMethodId(payment.getPaymentMethodId())
                    .paymentTypeId(payment.getPaymentTypeId())
                    .installments(payment.getInstallments())
                    .authorizationCode(authCode)
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

            if ("approved".equals(payment.getStatus())) {
                log.info("📤 Enviando evento de pago aprobado");
                sendPaymentApprovedEvent(paymentEntity, request);
            } else {
                log.warn("⏳ Pago en estado: {}. Esperando confirmación", payment.getStatus());
            }
            return paymentMapper.entityToResponse(paymentEntity);

        } catch (
                com.mercadopago.exceptions.MPApiException mpEx) {
            log.error("Error específico de Mercado Pago:");
            log.error(" Status Code: {}", mpEx.getStatusCode());
            log.error(" Message: {}", mpEx.getMessage());

            try {
                if (mpEx.getApiResponse() != null) {
                    log.error("API Response: {}", mpEx.getApiResponse().getContent());
                }
            } catch (
                    Exception e) {
                log.warn("No se pudo obtener detalles de la respuesta de MP");
            }

            throw mpEx;
        } catch (
                Exception ex) {
            log.error("Error general procesando pago: {}", ex.getMessage(), ex);
            throw ex;
        }
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

        log.info("📥 Actualizando pago. Payment ID: {}", payment.getId());

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
            String previousStatus = local.getStatus();

            local.setStatus(status);
            local.setStatusDetail(payment.getStatusDetail());

            if (payment.getAuthorizationCode() != null && !payment.getAuthorizationCode().isEmpty()) {
                local.setAuthorizationCode(payment.getAuthorizationCode());
                log.info("✅ Authorization Code actualizado: {}", payment.getAuthorizationCode());
            }

            if (payment.getDateApproved() != null && local.getDateApproved() == null) {
                local.setDateApproved(OffsetDateTime.ofInstant(payment.getDateApproved().toInstant(), ZoneOffset.UTC));
            }

            paymentRepository.save(local);
            log.info("✅ Pago actualizado: {} -> {}", previousStatus, status);

            if ("approved".equals(status) && !"approved".equals(previousStatus)) {
                log.info("📤 Pago aprobado, enviando evento a Kafka");
                sendPaymentApprovedEventFromEntity(local);
            }
        }
    }

    private void sendPaymentApprovedEventFromEntity(PaymentEntity payment) {
        try {
            PaymentApprovedEvent event = new PaymentApprovedEvent(
                    payment.getId(),
                    payment.getUserId(),
                    payment.getPayerEmail(),
                    payment.getPayerFirstName() + " " + payment.getPayerLastName(),
                    payment.getPlan().getId(),
                    payment.getPlan().getName(),
                    payment.getPlan().getDurationMonths(),
                    payment.getAmount(),
                    payment.getExternalReference(),
                    payment.getDateCreated(),
                    payment.getTransactionId()
            );

            kafkaTemplate.send("payment-approved-event-topic", event);
            log.info("📤 Evento enviado: {}", event);
        } catch (
                Exception e) {
            log.error("❌ Error enviando evento", e);
        }
    }

    private void sendPaymentApprovedEvent(PaymentEntity payment, DirectPaymentRequest request) {
        try {
            PaymentApprovedEvent event = new PaymentApprovedEvent(
                    payment.getId(),
                    payment.getUserId(),
                    payment.getPayerEmail(),
                    payment.getPayerFirstName() + " " + payment.getPayerLastName(),
                    payment.getPlan().getId(),
                    payment.getPlan().getName(),
                    payment.getPlan().getDurationMonths(),
                    payment.getAmount(),
                    payment.getExternalReference(),
                    payment.getDateCreated(),
                    payment.getTransactionId()
            );

            kafkaTemplate.send("payment-approved-event-topic", event);
            log.info("📤 Evento de pago aprobado enviado: {}", event);
        } catch (
                Exception e) {
            log.error("❌ Error enviando evento de pago aprobado", e);
        }
    }

    @Override
    @Transactional
    public void simulatePaymentApproval(String externalReference, String authorizationCode) {
        log.info("🔄 Simulando aprobación de pago para referencia: {}", externalReference);

        Optional<PaymentEntity> paymentOpt = paymentRepository.findByExternalReference(externalReference);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException("Pago no encontrado para la referencia: " + externalReference);
        }

        PaymentEntity payment = paymentOpt.get();
        payment.setStatus("approved");
        payment.setStatusDetail("Simulated approval");
        payment.setAuthorizationCode(authorizationCode);
        payment.setDateApproved(OffsetDateTime.now());

        paymentRepository.save(payment);

        log.info("✅ Pago simulado como aprobado. Referencia: {}, Authorization Code: {}", externalReference, authorizationCode);
        sendPaymentApprovedEventFromEntity(payment);
    }


}