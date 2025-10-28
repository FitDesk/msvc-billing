package com.msvcbilling.services.impl;

import com.mercadopago.client.customer.CustomerCardClient;
import com.mercadopago.client.customer.CustomerCardCreateRequest;
import com.mercadopago.client.customer.CustomerClient;
import com.mercadopago.client.customer.CustomerRequest;
import com.mercadopago.client.payment.*;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.customer.Customer;
import com.mercadopago.resources.customer.CustomerCard;
import com.mercadopago.resources.payment.Payment;
import com.msvcbilling.dtos.card.CreatePaymentMethodRequest;
import com.msvcbilling.dtos.card.PaymentWithSavedCardRequest;
import com.msvcbilling.dtos.card.SavedPaymentMethodDto;
import com.msvcbilling.dtos.card.UpdatePaymentMethodRequest;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.entities.PaymentEntity;
import com.msvcbilling.entities.PaymentMethodEntity;
import com.msvcbilling.entities.PlanEntity;
import com.msvcbilling.events.PaymentApprovedEvent;
import com.msvcbilling.exceptions.PaymentMethodValidationException;
import com.msvcbilling.mappers.PaymentMapper;
import com.msvcbilling.mappers.PaymentMethodMapper;
import com.msvcbilling.repository.PaymentMethodRepository;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.repository.PlanRepository;
import com.msvcbilling.services.PaymentMethodService;
import jakarta.xml.bind.ValidationException;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodMapper paymentMethodMapper;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentClient paymentClient;
    private final CustomerClient customerClient;
    private final CustomerCardClient customerCardClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final int MAX_CARDS_PER_USER = 5;

    @Override
    @Transactional
    public SavedPaymentMethodDto savePaymentMethod(
        UUID userId,
        CreatePaymentMethodRequest request
    ) {
        log.info(
            "Iniciando el guardado de método de pago para usuario: {}",
            userId
        );

        try {
            long cardCount = paymentMethodRepository.countActiveCardsByUser(
                userId
            );
            if (cardCount >= MAX_CARDS_PER_USER) {
                throw new PaymentMethodValidationException(
                    "Has alcanzado el límite máximo de " +
                        MAX_CARDS_PER_USER +
                        " tarjetas guardadas"
                );
            }

            // 1. Obtener o crear cliente en Mercado Pago
            String customerId = getOrCreateMpCustomer(request.payerEmail());

            // 2. Guardar la tarjeta en el cliente de Mercado Pago para obtener un ID permanente
            CustomerCardCreateRequest cardCreateRequest =
                CustomerCardCreateRequest.builder()
                    .token(request.cardToken())
                    .build();
            CustomerCard savedMpCard = customerCardClient.create(
                customerId,
                cardCreateRequest
            );
            String persistentCardId = savedMpCard.getId();
            log.info(
                "Tarjeta guardada en Mercado Pago con ID permanente: {}",
                persistentCardId
            );

            if (
                paymentMethodRepository.existsByUserIdAndCardToken(
                    userId,
                    persistentCardId
                )
            ) {
                throw new PaymentMethodValidationException(
                    "Esta tarjeta ya está guardada"
                );
            }

            if (request.setAsDefault()) {
                paymentMethodRepository
                    .findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(card -> {
                        card.setIsDefault(false);
                        paymentMethodRepository.save(card);
                    });
            }

            PaymentMethodEntity entity = PaymentMethodEntity.builder()
                .userId(userId)
                .cardToken(persistentCardId) // Guardar el ID permanente de la tarjeta
                .lastFourDigits(savedMpCard.getLastFourDigits())
                .cardHolderName(savedMpCard.getCardholder().getName())
                .cardBrand(savedMpCard.getPaymentMethod().getId().toUpperCase())
                .cardType(savedMpCard.getPaymentMethod().getPaymentTypeId())
                .expirationMonth(savedMpCard.getExpirationMonth())
                .expirationYear(savedMpCard.getExpirationYear())
                .isDefault(request.setAsDefault() || cardCount == 0)
                .isActive(true)
                .nickname(request.nickname())
                .build();

            entity = paymentMethodRepository.save(entity);
            log.info(
                "Método de pago guardado en la base de datos con ID: {}",
                entity.getId()
            );

            return paymentMethodMapper.toDto(entity);
        } catch (MPApiException e) {
            log.error(
                "Error de API de Mercado Pago al guardar la tarjeta: {}",
                e.getApiResponse().getContent(),
                e
            );
            throw new PaymentMethodValidationException(
                "Error al comunicar con el proveedor de pagos para guardar la tarjeta."
            );
        } catch (Exception e) {
            log.error("Error inesperado al guardar el método de pago", e);
            throw new RuntimeException(
                "Error inesperado en el servidor al guardar el método de pago."
            );
        }
    }

    private String getOrCreateMpCustomer(String email) throws MPApiException, MPException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("email", email);

        MPSearchRequest searchRequest = MPSearchRequest.builder().limit(1).offset(0).filters(filters).build();

        // Usar el tipo correcto que el compilador nos indicó
        MPResultsResourcesPage<Customer> searchResults = customerClient.search(searchRequest);

        if (searchResults.getResults() != null && !searchResults.getResults().isEmpty()) {
            log.info("Cliente de Mercado Pago encontrado para email: {}", email);
            return searchResults.getResults().get(0).getId();
        } else {
            log.info("No se encontró cliente de Mercado Pago. Creando uno nuevo para email: {}", email);
            CustomerRequest customerRequest = CustomerRequest.builder().email(email).build();
            Customer newCustomer = customerClient.create(customerRequest);
            return newCustomer.getId();
        }
    }


    @Override
    public List<SavedPaymentMethodDto> getUserPaymentMethods(UUID userId) {
        log.info("Obteniendo métodos de pago para usuario: {}", userId);
        List<PaymentMethodEntity> cards =
            paymentMethodRepository.findByUserIdAndIsActiveTrue(userId);
        return paymentMethodMapper.toDtoList(cards);
    }

    @Override
    public SavedPaymentMethodDto getPaymentMethod(UUID userId, UUID cardId) {
        PaymentMethodEntity card = paymentMethodRepository
            .findByIdAndUserId(cardId, userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Tarjeta no encontrada")
            );
        return paymentMethodMapper.toDto(card);
    }

    @Override
    @Transactional
    public SavedPaymentMethodDto updatePaymentMethod(
        UUID userId,
        UUID cardId,
        UpdatePaymentMethodRequest request
    ) {
        PaymentMethodEntity card = paymentMethodRepository
            .findByIdAndUserId(cardId, userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Tarjeta no encontrada")
            );

        if (request.nickname() != null) {
            card.setNickname(request.nickname());
        }

        if (request.setAsDefault() != null && request.setAsDefault()) {
            // Desmarcar otras tarjetas como default
            paymentMethodRepository.unsetDefaultForUser(userId, cardId);
            card.setIsDefault(true);
        }

        card = paymentMethodRepository.save(card);
        return paymentMethodMapper.toDto(card);
    }

    @Override
    @Transactional
    public void deletePaymentMethod(UUID userId, UUID cardId) {
        PaymentMethodEntity card = paymentMethodRepository
            .findByIdAndUserId(cardId, userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Tarjeta no encontrada")
            );

        card.setIsActive(false);
        paymentMethodRepository.save(card);

        if (card.getIsDefault()) {
            paymentMethodRepository
                .findByUserIdAndIsActiveTrue(userId)
                .stream()
                .findFirst()
                .ifPresent(newDefault -> {
                    newDefault.setIsDefault(true);
                    paymentMethodRepository.save(newDefault);
                });
        }

        log.info("Tarjeta {} eliminada para usuario {}", cardId, userId);
    }

    @Override
    @Transactional
    public PaymentResponse processPaymentWithSavedCard(
        PaymentWithSavedCardRequest request
    ) throws Exception {
        log.info(
            "Procesando pago con tarjeta guardada: {}",
            request.savedCardId()
        );

        PaymentMethodEntity savedCard = paymentMethodRepository
            .findByIdAndUserId(request.savedCardId(), request.userId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Tarjeta no encontrada")
            );

        if (savedCard.isExpired()) {
            throw new ValidationException("La tarjeta está expirada");
        }

        PlanEntity plan = planRepository
            .findById(request.planId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Plan no encontrado")
            );

        // Crear el pago con el token guardado
        String externalReference =
            "SAVED_CARD_" + request.userId() + "_" + System.currentTimeMillis();

        PaymentPayerRequest payerRequest = PaymentPayerRequest.builder()
            .email(request.payerEmail())
            .firstName(request.payerName())
            .build();

        PaymentAdditionalInfoRequest additionalInfo =
            PaymentAdditionalInfoRequest.builder()
                .items(
                    Collections.singletonList(
                        PaymentItemRequest.builder()
                            .id(savedCard.getId().toString())
                            .title("Pago con tarjeta guardada")
                            .description(
                                "Últimos 4 dígitos: " +
                                    savedCard.getLastFourDigits()
                            )
                            .quantity(1)
                            .unitPrice(request.amount())
                            .build()
                    )
                )
                .build();

        PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
            .transactionAmount(request.amount())
            .token(savedCard.getCardToken())
            .description(
                request.description() != null
                    ? request.description()
                    : "Pago FitDesk con tarjeta guardada"
            )
            .installments(
                request.installments() != null ? request.installments() : 1
            )
            .paymentMethodId(savedCard.getCardBrand().toLowerCase())
            .externalReference(externalReference)
            .payer(payerRequest)
            .statementDescriptor("FITDESK")
            .binaryMode(false)
            .additionalInfo(additionalInfo)
            .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-idempotency-key", UUID.randomUUID().toString());
        MPRequestOptions options = MPRequestOptions.builder()
            .customHeaders(headers)
            .build();

        Payment payment = paymentClient.create(paymentRequest, options);

        PaymentEntity paymentEntity = PaymentEntity.builder()
            .id(UUID.randomUUID())
            .userId(request.userId())
            .plan(plan)
            .externalReference(externalReference)
            .paymentId(payment.getId())
            .token(savedCard.getCardToken())
            .paymentMethodId(payment.getPaymentMethodId())
            .paymentTypeId(payment.getPaymentTypeId())
            .installments(payment.getInstallments())
            .authorizationCode(payment.getAuthorizationCode())
            .transactionId(payment.getId().toString())
            .amount(payment.getTransactionAmount())
            .currencyId(payment.getCurrencyId())
            .status(payment.getStatus())
            .statusDetail(payment.getStatusDetail())
            .dateCreated(OffsetDateTime.now())
            .dateApproved(
                payment.getStatus().equals("approved")
                    ? OffsetDateTime.now()
                    : null
            )
            .build();

        paymentEntity = paymentRepository.save(paymentEntity);

        log.info(
            "Pago procesado con tarjeta guardada. ID: {}, Status: {}",
            payment.getId(),
            payment.getStatus()
        );
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
    }

    private void sendPaymentApprovedEvent(
        PaymentEntity payment,
        PaymentWithSavedCardRequest request
    ) {
        try {
            PaymentApprovedEvent event = new PaymentApprovedEvent(
                payment.getId(),
                payment.getUserId(),
                request.payerEmail(),
                request.payerName(),
                payment.getPlan().getId(),
                payment.getPlan().getName(),
                payment.getPlan().getDurationMonths(),
                payment.getAmount(),
                payment.getExternalReference(),
                payment.getDateCreated(),
                payment.getTransactionId()
            );

            kafkaTemplate.send("payment-approved-event-topic", event);
            log.info(
                "Evento de pago aprobado (tarjeta guardada) enviado: {}",
                event
            );
        } catch (Exception e) {
            log.error(
                "Error enviando evento de pago aprobado (tarjeta guardada)",
                e
            );
        }
    }

    private String detectCardBrand(String cardNumber) {
        String cleaned = cardNumber.replace(" ", "");
        if (cleaned.startsWith("4")) return "VISA";
        if (
            cleaned.matches("^5[1-5].*") || cleaned.matches("^2[2-7].*")
        ) return "MASTERCARD";
        if (cleaned.matches("^3[47].*")) return "AMEX";
        if (cleaned.matches("^6(?:011|5).*")) return "DISCOVER";
        return "UNKNOWN";
    }
}
