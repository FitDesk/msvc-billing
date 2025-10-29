package com.msvcbilling.services.impl;

import static com.msvcbilling.utils.PaymentMethods.*;

import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.customer.CustomerClient;
import com.mercadopago.client.customer.CustomerRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.paymentmethod.PaymentMethodClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.customer.Customer;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.paymentmethod.PaymentMethod;
import com.msvcbilling.clients.MercadoPagoApiClient;
import com.msvcbilling.dtos.payment.*;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PaymentClient paymentClient;
    private final PaymentMethodClient paymentMethodClient;
    private final CustomerClient customerClient;
    private final PaymentMapper paymentMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MercadoPagoApiClient mercadoPagoApiClient;

    @Transactional
    @Override
    public PaymentResponse processDirectPayment(DirectPaymentRequest request)
        throws Exception {
        log.info(
            "Procesando pago directo para referencia: {}",
            request.externalReference()
        );

        PlanEntity plan = planRepository
            .findById(request.planId())
            .orElseThrow(() -> new PlanNotFoundException(request.planId()));

        if (!plan.getIsActive()) {
            throw new PlanNotActiveException(
                "El plan selecionado no esta activo"
            );
        }
        boolean isUpgrade = request.externalReference().startsWith("UPGRADE-");
        if (!isUpgrade && plan.getPrice().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException(
                "El monto no coincide con el precio del plan"
            );
        }

        if (isUpgrade) {
            log.info(
                "💰 Procesando UPGRADE - Monto prorrateado: {}, Plan nuevo: {}",
                request.amount(),
                plan.getName()
            );
        }
        Optional<PaymentEntity> existing =
            paymentRepository.findByExternalReference(
                request.externalReference()
            );
        if (existing.isPresent()) {
            PaymentEntity existingPayment = existing.get();
            log.info(
                " Pago ya existe, retornando existente: {}",
                existingPayment.getPaymentId()
            );
            return paymentMapper.entityToResponse(existingPayment);
        }

        try {
            log.info(
                " Creando pago - Monto: {}, Email: {}, Método: {}",
                request.amount(),
                request.payerEmail(),
                request.paymentMethodId()
            );
            String customerId = getOrCreateMpCustomer(request.payerEmail());
            IdentificationRequest identification =
                IdentificationRequest.builder()
                    .type(request.identificationType())
                    .number(request.identificationNumber())
                    .build();

            PaymentPayerRequest payer = PaymentPayerRequest.builder()
                .id(customerId) // Añadir el ID del cliente
                .email(request.payerEmail())
                .firstName(request.payerFirstName())
                .lastName(request.payerLastName())
                .identification(identification)
                .build();

            PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                .transactionAmount(request.amount())
                .token(request.token())
                .description(
                    request.description() != null
                        ? request.description()
                        : "Pago FitDesk"
                )
                .installments(request.installments())
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

            log.info(
                " Pago creado en Mercado Pago. ID: {}, Status: {}, Detail: {}",
                payment.getId(),
                payment.getStatus(),
                payment.getStatusDetail()
            );
            String authCode = payment.getAuthorizationCode();
            if (authCode == null || authCode.isEmpty()) {
                authCode = "PENDING";
                log.warn(
                    " Pago en proceso. AuthCode será actualizado posteriormente"
                );
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
                .dateCreated(
                    payment.getDateCreated() != null
                        ? OffsetDateTime.ofInstant(
                              payment.getDateCreated().toInstant(),
                              ZoneOffset.UTC
                          )
                        : OffsetDateTime.now()
                )
                .dateApproved(
                    payment.getDateApproved() != null
                        ? OffsetDateTime.ofInstant(
                              payment.getDateApproved().toInstant(),
                              ZoneOffset.UTC
                          )
                        : null
                )
                .build();

            paymentRepository.save(paymentEntity);

            if ("approved".equals(payment.getStatus())) {
                log.info(" Enviando evento de pago aprobado");
                sendPaymentApprovedEvent(paymentEntity, request);
            } else {
                log.warn(
                    "Pago en estado: {}. Esperando confirmación",
                    payment.getStatus()
                );
            }
            return paymentMapper.entityToResponse(paymentEntity);
        } catch (MPApiException mpEx) {
            log.error("Error específico de Mercado Pago:");
            log.error(" Status Code: {}", mpEx.getStatusCode());
            log.error(" Message: {}", mpEx.getMessage());

            try {
                if (mpEx.getApiResponse() != null) {
                    log.error(
                        "API Response: {}",
                        mpEx.getApiResponse().getContent()
                    );
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener detalles de la respuesta de MP");
            }

            throw mpEx;
        } catch (Exception ex) {
            log.error("Error general procesando pago: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional
    @Override
    public PaymentResponse processPlanUpgrade(PlanUpgradeRequestDto request)
        throws Exception {
        log.info(
            "🔄 Iniciando proceso de upgrade para usuario: {}",
            request.userId()
        );

        //  Buscar el pago activo actual
        PaymentEntity currentPayment = findActivePaymentForUser(
            request.userId()
        );
        PlanEntity currentPlan = currentPayment.getPlan();
        PlanEntity newPlan = findPlanById(request.newPlanId());

        //  Validaciones
        validatePlanUpgrade(currentPlan, newPlan);
        BigDecimal upgradeCost = calculateProratedUpgradeCost(
            currentPayment,
            newPlan
        );
        validateUpgradeCost(upgradeCost);

        log.info("💰 Costo de upgrade calculado: {}", upgradeCost);

        DirectPaymentRequest upgradePaymentRequest =
            createUpgradePaymentRequest(
                request,
                upgradeCost,
                newPlan,
                currentPayment
            );

        // 4️⃣ Procesar el nuevo pago
        PaymentResponse newPaymentResponse = processDirectPayment(
            upgradePaymentRequest
        );

        // 5️⃣ Cancelar el pago anterior SOLO si el nuevo fue aprobado
        if ("approved".equals(newPaymentResponse.status())) {
            log.info(
                "✅ Nuevo pago aprobado. Cancelando pago anterior ID: {}",
                currentPayment.getId()
            );
            cancelOldSubscriptionAndUpdateState(currentPayment);
        } else {
            log.warn(
                "⚠️ Nuevo pago NO aprobado (estado: {}). No se cancela el pago anterior.",
                newPaymentResponse.status()
            );
        }

        return newPaymentResponse;
    }

    //    //    @Override
    //    @Transactional
    //    public void processRefund(Long paymentId) {
    //        log.info("Iniciando proceso de devolución para el pago con ID local: {}", paymentId);
    //
    //        Payment payment = paymentRepository.findById(paymentId)
    //                .orElseThrow(() -> new EntityNotFoundException("No se encontró el pago con ID " + paymentId));
    //
    //        if (!"approved".equals(payment.getStatus())) {
    //            throw new IllegalArgumentException("Solo se pueden devolver pagos 'approved'.");
    //        }
    //
    //        Long mercadoPagoPaymentId = payment.getMercadoPagoPaymentId();
    //        if (mercadoPagoPaymentId == null) {
    //            throw new IllegalStateException("No se encontró el ID del pago de Mercado Pago.");
    //        }
    //
    //        try {
    //            // ¡AQUÍ ESTÁ EL CAMBIO! Usamos el cliente Feign para la devolución.
    //            mercadoPagoApiClient.createRefund(mercadoPagoPaymentId);
    //            log.info("Devolución para el pago de MP {} procesada exitosamente vía Feign.", mercadoPagoPaymentId);
    //
    //            payment.setStatus("refunded");
    //            payment.setReason("Devolución procesada a petición.");
    //            paymentRepository.save(payment);
    //
    //        } catch (
    //                Exception e) {
    //            log.error("Error al procesar devolución con Feign para el pago de MP {}. Error: {}", mercadoPagoPaymentId, e.getMessage());
    //            throw new RuntimeException("Fallo al procesar la devolución en Mercado Pago.", e);
    //        }
    //    }

    @Transactional
    @Override
    public PaymentResponse getPaymentStatus(String externalReference) {
        log.info(
            "Consultando estado de pago para referencia: {}",
            externalReference
        );

        Optional<PaymentEntity> paymentOpt =
            paymentRepository.findByExternalReference(externalReference);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException(
                "Pago no encontrado para la referencia: " + externalReference
            );
        }

        PaymentEntity paymentEntity = paymentOpt.get();

        if (paymentEntity.getPaymentId() != null) {
            try {
                Payment mpPayment = paymentClient.get(
                    paymentEntity.getPaymentId()
                );
                if (
                    mpPayment != null &&
                    !Objects.equals(
                        paymentEntity.getStatus(),
                        mpPayment.getStatus()
                    )
                ) {
                    updatePaymentFromMpPayment(mpPayment);
                    paymentEntity = paymentRepository
                        .findByExternalReference(externalReference)
                        .orElse(paymentEntity);
                }
            } catch (Exception e) {
                log.warn(
                    "Error consultando estado en Mercado Pago: {}",
                    e.getMessage()
                );
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
                return response
                    .getResults()
                    .stream()
                    .filter(
                        pm ->
                            "credit_card".equals(pm.getPaymentTypeId()) ||
                            "debit_card".equals(pm.getPaymentTypeId())
                    )
                    .map(PaymentMethod::getId)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Error consultando métodos de pago: {}", e.getMessage());
        }

        return Arrays.asList("visa", "master", "amex");
    }

    @Override
    public UpgradeCostResponse calculateUpgradeCost(
        UpgradeCostCalculationRequest request
    ) throws Exception {
        log.info(
            "📊 Calculando costo de upgrade para usuario: {} al plan: {}",
            request.userId(),
            request.newPlanId()
        );

        //  Buscar el pago activo actual
        PaymentEntity currentPayment = findActivePaymentForUser(
            request.userId()
        );
        PlanEntity currentPlan = currentPayment.getPlan();
        PlanEntity newPlan = findPlanById(request.newPlanId());

        //  Validaciones
        validatePlanUpgrade(currentPlan, newPlan);

        //  Calcular el costo prorrateado
        OffsetDateTime startDate = currentPayment.getDateApproved();
        if (startDate == null) {
            startDate = currentPayment.getDateCreated();
        }
        OffsetDateTime now = OffsetDateTime.now();

        long cycleDurationInDays = currentPlan.getDurationMonths() * 30L;
        long daysElapsed = ChronoUnit.DAYS.between(startDate, now);
        long daysRemaining = cycleDurationInDays - daysElapsed;

        BigDecimal dailyCostCurrent = currentPlan
            .getPrice()
            .divide(
                BigDecimal.valueOf(cycleDurationInDays),
                10,
                RoundingMode.HALF_UP
            );
        BigDecimal dailyCostNew = newPlan
            .getPrice()
            .divide(
                BigDecimal.valueOf(cycleDurationInDays),
                10,
                RoundingMode.HALF_UP
            );

        BigDecimal unusedCredit = dailyCostCurrent.multiply(
            BigDecimal.valueOf(daysRemaining)
        );
        BigDecimal remainingCostNewPlan = dailyCostNew.multiply(
            BigDecimal.valueOf(daysRemaining)
        );
        BigDecimal upgradeCost = remainingCostNewPlan
            .subtract(unusedCredit)
            .setScale(2, RoundingMode.HALF_UP);

        //  Validar que el costo sea positivo
        validateUpgradeCost(upgradeCost);

        log.info(
            "💰 Cálculo completado - Upgrade cost: {}, Días restantes: {}",
            upgradeCost,
            daysRemaining
        );

        return new UpgradeCostResponse(
            newPlan.getPrice(),
            upgradeCost,
            unusedCredit,
            daysRemaining,
            newPlan.getName(),
            newPlan.getDescription()
        );
    }

    @Override
    public void updatePaymentFromMpPayment(Payment payment) {
        if (payment == null) return;

        log.info(" Actualizando pago. Payment ID: {}", payment.getId());

        String extRef = payment.getExternalReference();
        String status = payment.getStatus() != null
            ? payment.getStatus()
            : "unknown";

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

            if (
                payment.getAuthorizationCode() != null &&
                !payment.getAuthorizationCode().isEmpty()
            ) {
                local.setAuthorizationCode(payment.getAuthorizationCode());
                log.info(
                    " Authorization Code actualizado: {}",
                    payment.getAuthorizationCode()
                );
            }

            if (
                payment.getDateApproved() != null &&
                local.getDateApproved() == null
            ) {
                local.setDateApproved(
                    OffsetDateTime.ofInstant(
                        payment.getDateApproved().toInstant(),
                        ZoneOffset.UTC
                    )
                );
            }

            paymentRepository.save(local);
            log.info("Pago actualizado: {} -> {}", previousStatus, status);

            if (
                "approved".equals(status) && !"approved".equals(previousStatus)
            ) {
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
        } catch (Exception e) {
            log.error(" Error enviando evento", e);
        }
    }

    private void sendPaymentApprovedEvent(
        PaymentEntity payment,
        DirectPaymentRequest request
    ) {
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
        } catch (Exception e) {
            log.error(" Error enviando evento de pago aprobado", e);
        }
    }

    private String getOrCreateMpCustomer(String email)
        throws MPApiException, MPException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("email", email);

        MPSearchRequest searchRequest = MPSearchRequest.builder()
            .limit(1)
            .offset(0)
            .filters(filters)
            .build();

        MPResultsResourcesPage<Customer> searchResults = customerClient.search(
            searchRequest
        );

        if (
            searchResults.getResults() != null &&
            !searchResults.getResults().isEmpty()
        ) {
            log.info(
                "Cliente de Mercado Pago encontrado para email: {}",
                email
            );
            return searchResults.getResults().get(0).getId();
        } else {
            log.info(
                "No se encontró cliente de Mercado Pago. Creando uno nuevo para email: {}",
                email
            );
            CustomerRequest customerRequest = CustomerRequest.builder()
                .email(email)
                .build();
            Customer newCustomer = customerClient.create(customerRequest);
            return newCustomer.getId();
        }
    }

    @Override
    @Transactional
    public void simulatePaymentApproval(
        String externalReference,
        String authorizationCode
    ) {
        log.info(
            "🔄 Simulando aprobación de pago para referencia: {}",
            externalReference
        );

        Optional<PaymentEntity> paymentOpt =
            paymentRepository.findByExternalReference(externalReference);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException(
                "Pago no encontrado para la referencia: " + externalReference
            );
        }

        PaymentEntity payment = paymentOpt.get();
        payment.setStatus("approved");
        payment.setStatusDetail("Simulated approval");
        payment.setAuthorizationCode(authorizationCode);
        payment.setDateApproved(OffsetDateTime.now());

        paymentRepository.save(payment);

        log.info(
            "Pago simulado como aprobado. Referencia: {}, Authorization Code: {}",
            externalReference,
            authorizationCode
        );
        sendPaymentApprovedEventFromEntity(payment);
    }

    @Override
    public Page<PaymentDetailsResponseDto> getAllPaymentsDetails(
        Pageable pageable,
        String status,
        String paymentMethodId,
        OffsetDateTime startDate,
        OffsetDateTime endDate
    ) {
        log.info(
            "Buscando pagos con filtros - Status: {}, Método de Pago: {}, Fecha Inicio: {}, Fecha Fin: {}",
            status,
            paymentMethodId,
            startDate,
            endDate
        );

        Specification<PaymentEntity> spec = PaymentSpecification.hasStatus(
            status
        )
            .and(PaymentSpecification.hasPaymentMethod(paymentMethodId))
            .and(PaymentSpecification.isBetweenDates(startDate, endDate));

        Page<PaymentEntity> paymentsPage = paymentRepository.findAll(
            spec,
            pageable
        );

        log.info(
            "Se encontraron {} pagos en la página {} de {}",
            paymentsPage.getNumberOfElements(),
            paymentsPage.getNumber(),
            paymentsPage.getTotalPages()
        );

        return paymentsPage.map(paymentMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatisticsResponseDto getDashboardStatistics() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Rangos de fecha para el mes actual y el anterior
        OffsetDateTime startOfCurrentMonth = now
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
        OffsetDateTime endOfCurrentMonth = startOfCurrentMonth
            .plusMonths(1)
            .minusNanos(1);
        OffsetDateTime startOfPreviousMonth = startOfCurrentMonth.minusMonths(
            1
        );
        OffsetDateTime endOfPreviousMonth = startOfCurrentMonth.minusNanos(1);

        //  Ingresos Mensuales
        BigDecimal currentMonthRevenue =
            paymentRepository.findTotalRevenueBetween(
                startOfCurrentMonth,
                endOfCurrentMonth
            );
        BigDecimal previousMonthRevenue =
            paymentRepository.findTotalRevenueBetween(
                startOfPreviousMonth,
                endOfPreviousMonth
            );
        StatisticDataDto<BigDecimal> revenueStats = createStatisticData(
            currentMonthRevenue,
            previousMonthRevenue
        );

        //  Nuevos Miembros
        long currentMonthMembers = paymentRepository.countNewMembersBetween(
            startOfCurrentMonth,
            endOfCurrentMonth
        );
        long previousMonthMembers = paymentRepository.countNewMembersBetween(
            startOfPreviousMonth,
            endOfPreviousMonth
        );
        StatisticDataDto<Long> memberStats = createStatisticData(
            currentMonthMembers,
            previousMonthMembers
        );

        //  Estadísticas Adicionales
        long totalApprovedPayments =
            paymentRepository.countTotalApprovedPayments();
        List<PlanDistributionDto> topPlans = paymentRepository.findTopPlans();
        List<StatusDistributionDto> paymentStatusDistribution =
            paymentRepository.findPaymentStatusDistribution();

        return new DashboardStatisticsResponseDto(
            revenueStats,
            memberStats,
            totalApprovedPayments,
            topPlans,
            paymentStatusDistribution
        );
    }

    private <T extends Number> StatisticDataDto<T> createStatisticData(
        T currentValue,
        T previousValue
    ) {
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
        return BigDecimal.valueOf(change)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private String getTrend(double percentageChange) {
        if (percentageChange > 0) return "up";
        if (percentageChange < 0) return "down";
        return "neutral";
    }

    private BigDecimal calculateProratedUpgradeCost(
        PaymentEntity currentPayment,
        PlanEntity newPlan
    ) {
        PlanEntity currentPlan = currentPayment.getPlan();
        OffsetDateTime startDate = currentPayment.getDateApproved(); //cuando en realidad empezo la sub
        if (startDate == null) {
            startDate = currentPayment.getDateCreated();
        }
        OffsetDateTime now = OffsetDateTime.now();

        long cycleDurationInDays = currentPlan.getDurationMonths() * 30L; //  30 días por mes
        long daysElapsed = ChronoUnit.DAYS.between(startDate, now);
        long daysRemaining = cycleDurationInDays - daysElapsed;

        log.info(
            "Cálculo de prorrateo: Días en ciclo: {}, Días transcurridos: {}, Días restantes: {}",
            cycleDurationInDays,
            daysElapsed,
            daysRemaining
        );

        if (daysRemaining <= 0) {
            return newPlan.getPrice();
        }

        BigDecimal dailyCostCurrent = currentPlan
            .getPrice()
            .divide(
                BigDecimal.valueOf(cycleDurationInDays),
                10,
                RoundingMode.HALF_UP
            );
        BigDecimal dailyCostNew = newPlan
            .getPrice()
            .divide(
                BigDecimal.valueOf(cycleDurationInDays),
                10,
                RoundingMode.HALF_UP
            );

        BigDecimal unusedCredit = dailyCostCurrent.multiply(
            BigDecimal.valueOf(daysRemaining)
        );
        BigDecimal remainingCostNewPlan = dailyCostNew.multiply(
            BigDecimal.valueOf(daysRemaining)
        );

        BigDecimal proratedCost = remainingCostNewPlan.subtract(unusedCredit);
        return proratedCost.setScale(2, RoundingMode.HALF_UP);
    }

    private void cancelOldSubscriptionAndUpdateState(PaymentEntity oldPayment) {
        try {
            log.info(
                "🔴 Cancelando pago anterior ID: {} (Payment ID: {})",
                oldPayment.getId(),
                oldPayment.getPaymentId()
            );

            // ✅ Actualizar el estado del pago anterior en la BD
            oldPayment.setStatus("cancelled");
            oldPayment.setStatusDetail("Cancelled due to plan upgrade");
            paymentRepository.save(oldPayment);

            log.info(
                "✅ Pago anterior actualizado a 'cancelled' en la base de datos"
            );

            // 🔄 Si tenía subscription ID, cancelarla en Mercado Pago
            if (
                oldPayment.getMercadoPagoSubscriptionId() != null &&
                !oldPayment.getMercadoPagoSubscriptionId().isEmpty()
            ) {
                log.info(
                    "📞 Cancelando suscripción en Mercado Pago: {}",
                    oldPayment.getMercadoPagoSubscriptionId()
                );

                try {
                    mercadoPagoApiClient.cancelSubscription(
                        oldPayment.getMercadoPagoSubscriptionId(),
                        new SubscriptionCancelRequest("cancelled")
                    );
                    log.info("✅ Suscripción cancelada en Mercado Pago");
                } catch (Exception mpEx) {
                    log.error(
                        "❌ Error cancelando suscripción en MP (no crítico): {}",
                        mpEx.getMessage()
                    );
                    // No lanzamos la excepción porque ya actualizamos la BD
                }
            }

            // 🔄 Intentar reembolso si es necesario (opcional)
            if (oldPayment.getPaymentId() != null) {
                try {
                    log.info(
                        "💰 Intentando reembolso del pago ID: {}",
                        oldPayment.getPaymentId()
                    );
                    mercadoPagoApiClient.createRefund(
                        oldPayment.getPaymentId()
                    );
                    log.info("✅ Reembolso procesado correctamente");

                    oldPayment.setStatus("refunded");
                    oldPayment.setStatusDetail("Refunded due to plan upgrade");
                    paymentRepository.save(oldPayment);
                } catch (Exception refundEx) {
                    log.warn(
                        "⚠️ No se pudo procesar el reembolso (puede que no sea elegible): {}",
                        refundEx.getMessage()
                    );
                    // Mantenemos el estado como "cancelled"
                }
            }
        } catch (Exception e) {
            log.error(
                "❌ Error crítico cancelando pago anterior: {}",
                e.getMessage(),
                e
            );
            throw new RuntimeException(
                "Error al cancelar el pago anterior: " + e.getMessage()
            );
        }
    }

    private PaymentEntity findActivePaymentForUser(UUID userId) {
        log.debug("Buscando pago activo para el usuario: {}", userId);
        return paymentRepository
            .findFirstByUserIdAndStatusOrderByDateApprovedDesc(
                userId,
                "approved"
            )
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "No se encontró un plan activo para el usuario " + userId
                )
            );
    }

    private PlanEntity findPlanById(UUID planId) {
        log.debug("Buscando plan con ID: {}", planId);
        return planRepository
            .findById(planId)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "El nuevo plan con ID " + planId + " no existe."
                )
            );
    }

    private DirectPaymentRequest createUpgradePaymentRequest(
        PlanUpgradeRequestDto upgradeRequest,
        BigDecimal amountToCharge,
        PlanEntity newPlan,
        PaymentEntity currentPayment
    ) {
        String externalReference =
            "UPGRADE-" +
            upgradeRequest.userId() +
            "-" +
            System.currentTimeMillis();
        log.debug(
            "Creando DirectPaymentRequest con referencia externa: {}",
            externalReference
        );

        return new DirectPaymentRequest(
            externalReference,
            upgradeRequest.userId(),
            newPlan.getId(),
            amountToCharge,
            currentPayment.getPayerEmail(),
            currentPayment.getPayerFirstName(),
            currentPayment.getPayerLastName(),
            "Upgrade al plan " + newPlan.getName(),
            upgradeRequest.token(),
            upgradeRequest.installments(),
            upgradeRequest.paymentMethodId(),
            currentPayment.getPayerIdentificationType(),
            currentPayment.getPayerIdentificationNumber()
        );
    }
}
