package com.msvcbilling.services.impl;

import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.paymentmethod.PaymentMethodClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.paymentmethod.PaymentMethod;
import com.msvcbilling.dtos.payment.DirectPaymentRequest;
import com.msvcbilling.dtos.payment.PaymentDetailsResponseDto;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.dtos.payment.PlanUpgradeRequestDto;
import com.msvcbilling.dtos.statistics.DashboardStatisticsResponseDto;
import com.msvcbilling.dtos.statistics.PlanDistributionDto;
import com.msvcbilling.dtos.statistics.StatisticDataDto;
import com.msvcbilling.dtos.statistics.StatusDistributionDto;
import com.msvcbilling.entities.PaymentEntity;
import com.msvcbilling.entities.PlanEntity;
import com.msvcbilling.events.PaymentApprovedEvent;
import com.msvcbilling.exceptions.PlanNotActiveException;
import com.msvcbilling.exceptions.PlanNotFoundException;
import com.msvcbilling.mappers.PaymentMapper;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.repository.PlanRepository;
import com.msvcbilling.services.PaymentService;
import com.msvcbilling.specification.PaymentSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
        log.info("Procesando pago directo para referencia: {}", request.externalReference());

        PlanEntity plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new PlanNotFoundException(request.planId()));

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
            log.info(" Creando pago - Monto: {}, Email: {}, Método: {}",
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


            log.info(" Pago creado en Mercado Pago. ID: {}, Status: {}, Detail: {}",
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
                log.info(" Enviando evento de pago aprobado");
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
    @Override
    @Transactional
    public PaymentResponse processPlanUpgrade(PlanUpgradeRequestDto request) throws Exception {
        PlanEntity newPlan = planRepository.findById(request.newPlanId())
                .orElseThrow(() -> new EntityNotFoundException("El nuevo plan con ID " + request.newPlanId() + " no fue encontrado."));

        PaymentEntity currentPayment = paymentRepository.findFirstByUserIdAndStatusOrderByDateApprovedDesc(request.userId(), "approved")
                .orElseThrow(() -> new IllegalStateException("El usuario no tiene una suscripción activa para actualizar."));

        PlanEntity currentPlan = currentPayment.getPlan();

        OffsetDateTime expirationDate = currentPayment.getDateApproved().plusMonths(currentPlan.getDurationMonths());
        long daysRemaining = ChronoUnit.DAYS.between(OffsetDateTime.now(), expirationDate);

        if (daysRemaining <= 0) {
            throw new IllegalStateException("Tu plan actual ha expirado o está a punto de expirar. Realiza una compra normal.");
        }


        long totalDaysInCurrentPlan = (long) currentPlan.getDurationMonths() * 30;
        BigDecimal pricePerDay = currentPlan.getPrice().divide(BigDecimal.valueOf(totalDaysInCurrentPlan), 2, RoundingMode.HALF_UP);
        BigDecimal remainingValue = pricePerDay.multiply(BigDecimal.valueOf(daysRemaining));


        BigDecimal amountToCharge = newPlan.getPrice().subtract(remainingValue);

        log.info("Upgrade de plan: Nuevo plan '{}' ({}). Plan actual '{}' ({}). Valor restante: {}. Monto a cobrar: {}",
                newPlan.getName(), newPlan.getPrice(), currentPlan.getName(), currentPlan.getPrice(), remainingValue, amountToCharge);


        if (amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("El cambio de plan no requiere pago (monto: {}). Se procesará como un cambio directo.", amountToCharge);


            currentPayment.setStatus("UPGRADED");
            paymentRepository.save(currentPayment);


            PaymentEntity newPaymentEntity = PaymentEntity.builder()
                    .userId(request.userId())
                    .plan(newPlan)
                    .amount(newPlan.getPrice())
                    .payerEmail(currentPayment.getPayerEmail())
                    .status("approved")
                    .statusDetail("Upgrade sin costo")
                    .dateApproved(OffsetDateTime.now())
                    .externalReference("UPGRADE-" + UUID.randomUUID())
                    .build();

            paymentRepository.save(newPaymentEntity);


            sendPaymentApprovedEventFromEntity(newPaymentEntity);

            return paymentMapper.entityToResponse(newPaymentEntity);
        }

        DirectPaymentRequest paymentRequest = new DirectPaymentRequest(
                "UPGRADE-" + UUID.randomUUID(),
                request.userId(),
                request.newPlanId(),
                amountToCharge,
                currentPayment.getPayerEmail(),
                currentPayment.getPayerFirstName(),
                currentPayment.getPayerLastName(),
                "Upgrade al plan: " + newPlan.getName(),
                request.token(),
                request.installments(),
                request.paymentMethodId(),
                request.identificationType(),
                request.identificationNumber()
        );

        currentPayment.setStatus("UPGRADED");
        paymentRepository.save(currentPayment);

        return processDirectPayment(paymentRequest);
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

        log.info(" Actualizando pago. Payment ID: {}", payment.getId());

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
                log.info(" Authorization Code actualizado: {}", payment.getAuthorizationCode());
            }

            if (payment.getDateApproved() != null && local.getDateApproved() == null) {
                local.setDateApproved(OffsetDateTime.ofInstant(payment.getDateApproved().toInstant(), ZoneOffset.UTC));
            }

            paymentRepository.save(local);
            log.info("Pago actualizado: {} -> {}", previousStatus, status);

            if ("approved".equals(status) && !"approved".equals(previousStatus)) {
                log.info(" Pago aprobado, enviando evento a Kafka");
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
            log.info(" Evento enviado: {}", event);
        } catch (
                Exception e) {
            log.error(" Error enviando evento", e);
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
            log.info(" Evento de pago aprobado enviado: {}", event);
        } catch (
                Exception e) {
            log.error(" Error enviando evento de pago aprobado", e);
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

        log.info("Pago simulado como aprobado. Referencia: {}, Authorization Code: {}", externalReference, authorizationCode);
        sendPaymentApprovedEventFromEntity(payment);
    }

    @Override
    public Page<PaymentDetailsResponseDto> getAllPaymentsDetails(Pageable pageable, String status, String paymentMethodId, OffsetDateTime startDate, OffsetDateTime endDate) {
        log.info("Buscando pagos con filtros - Status: {}, Método de Pago: {}, Fecha Inicio: {}, Fecha Fin: {}",
                status, paymentMethodId, startDate, endDate);

        Specification<PaymentEntity> spec = PaymentSpecification.hasStatus(status)
                .and(PaymentSpecification.hasPaymentMethod(paymentMethodId))
                .and(PaymentSpecification.isBetweenDates(startDate, endDate));

        Page<PaymentEntity> paymentsPage = paymentRepository.findAll(spec, pageable);

        log.info("Se encontraron {} pagos en la página {} de {}", paymentsPage.getNumberOfElements(), paymentsPage.getNumber(), paymentsPage.getTotalPages());

        return paymentsPage.map(paymentMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatisticsResponseDto getDashboardStatistics() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Rangos de fecha para el mes actual y el anterior
        OffsetDateTime startOfCurrentMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endOfCurrentMonth = startOfCurrentMonth.plusMonths(1).minusNanos(1);
        OffsetDateTime startOfPreviousMonth = startOfCurrentMonth.minusMonths(1);
        OffsetDateTime endOfPreviousMonth = startOfCurrentMonth.minusNanos(1);

        //  Ingresos Mensuales
        BigDecimal currentMonthRevenue = paymentRepository.findTotalRevenueBetween(startOfCurrentMonth, endOfCurrentMonth);
        BigDecimal previousMonthRevenue = paymentRepository.findTotalRevenueBetween(startOfPreviousMonth, endOfPreviousMonth);
        StatisticDataDto<BigDecimal> revenueStats = createStatisticData(currentMonthRevenue, previousMonthRevenue);

        //  Nuevos Miembros
        long currentMonthMembers = paymentRepository.countNewMembersBetween(startOfCurrentMonth, endOfCurrentMonth);
        long previousMonthMembers = paymentRepository.countNewMembersBetween(startOfPreviousMonth, endOfPreviousMonth);
        StatisticDataDto<Long> memberStats = createStatisticData(currentMonthMembers, previousMonthMembers);

        //  Estadísticas Adicionales
        long totalApprovedPayments = paymentRepository.countTotalApprovedPayments();
        List<PlanDistributionDto> topPlans = paymentRepository.findTopPlans();
        List<StatusDistributionDto> paymentStatusDistribution = paymentRepository.findPaymentStatusDistribution();

        return new DashboardStatisticsResponseDto(
                revenueStats,
                memberStats,
                totalApprovedPayments,
                topPlans,
                paymentStatusDistribution
        );
    }


    private <T extends Number> StatisticDataDto<T> createStatisticData(T currentValue, T previousValue) {
        double current = currentValue.doubleValue();
        double previous = previousValue.doubleValue();
        double percentageChange = calculatePercentageChange(current, previous);
        String trend = getTrend(percentageChange);

        return new StatisticDataDto<>(currentValue, percentageChange, trend);
    }

    private double calculatePercentageChange(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        double change = ((current - previous) / previous) * 100.0;
        return BigDecimal.valueOf(change).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String getTrend(double percentageChange) {
        if (percentageChange > 0)
            return "up";
        if (percentageChange < 0)
            return "down";
        return "neutral";
    }

}
