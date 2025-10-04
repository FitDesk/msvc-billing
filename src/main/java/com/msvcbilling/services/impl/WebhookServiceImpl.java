package com.msvcbilling.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.msvcbilling.entities.WebhookEventEntity;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.repository.WebhookEventRepository;
import com.msvcbilling.services.PaymentService;
import com.msvcbilling.services.WebhookService;
import com.msvcbilling.utils.WebhookSignatureValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    @Transactional
    @Override
    public boolean handleWebhook(Map<String, String> headers, String body) {
        // 1) Validar firma
        String signatureHeader = resolveSignatureHeader(headers);
        if (!WebhookSignatureValidator.isValidSignature(webhookSecret, body, signatureHeader)) {
            log.warn("Firma inválida para webhook");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            // 2) Extraer event ID
            String eventId = extractEventId(root);
            String topic = extractTopic(root);
            String resourceId = extractResourceId(root);

            if (eventId == null) {
                if (resourceId == null) {
                    log.warn("Webhook sin id ni resource id, ignorando");
                    return false;
                }
                eventId = topic + ":" + resourceId;
            }

            // 3) Verificar idempotencia
            if (webhookEventRepository.existsById(eventId)) {
                log.info("Evento de webhook ya procesado: {}", eventId);
                return false;
            }

            // 4) Extraer payment ID
            String paymentId = extractPaymentId(root);
            if (paymentId == null) {
                log.warn("No se obtuvo paymentId del webhook: {}", eventId);
                saveWebhookEvent(eventId, topic, resourceId, body);
                return false;
            }

            // 5) Consultar estado en Mercado Pago
            try {
                Payment payment = paymentClient.get(Long.valueOf(paymentId));
                if (payment == null) {
                    log.warn("No se encontró payment en MP id={}", paymentId);
                } else {
                    // 6) Actualizar estado local
                    paymentService.updatePaymentFromMpPayment(payment);
                }
            } catch (
                    Exception e) {
                log.error("Error consultando payment en Mercado Pago: {}", e.getMessage());
            }

            // 7) Marcar como procesado
            saveWebhookEvent(eventId, topic, resourceId, body);

            return true;

        } catch (
                ResponseStatusException rse) {
            throw rse;
        } catch (
                Exception ex) {
            log.error("Error procesando webhook", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "processing error");
        }
    }

    private void saveWebhookEvent(String eventId, String topic, String resourceId, String body) {
        WebhookEventEntity event = WebhookEventEntity.builder()
                .id(eventId)
                .topic(topic)
                .resourceId(resourceId)
                .rawBody(body)
                .processedAt(OffsetDateTime.now())
                .build();
        webhookEventRepository.save(event);
    }

    private String resolveSignatureHeader(Map<String, String> headers) {
        return headers.getOrDefault("x-signature",
                headers.getOrDefault("X-Signature",
                        headers.getOrDefault("x-hub-signature-256", headers.get("X-Hub-Signature-256"))));
    }

    private String extractEventId(JsonNode root) {
        if (root.has("id"))
            return root.get("id").asText(null);
        return null;
    }

    private String extractTopic(JsonNode root) {
        if (root.has("topic"))
            return root.get("topic").asText(null);
        if (root.has("type"))
            return root.get("type").asText(null);
        return "unknown";
    }

    private String extractResourceId(JsonNode root) {
        if (root.has("data") && root.get("data").has("id"))
            return root.get("data").get("id").asText(null);
        if (root.has("resource") && root.get("resource").has("id"))
            return root.get("resource").get("id").asText(null);
        if (root.has("id"))
            return root.get("id").asText(null);
        return null;
    }

    private String extractPaymentId(JsonNode root) {
        if (root.has("data") && root.get("data").has("id"))
            return root.get("data").get("id").asText(null);
        if (root.has("resource") && root.get("resource").has("id"))
            return root.get("resource").get("id").asText(null);
        if (root.has("id"))
            return root.get("id").asText(null);
        return null;
    }
}