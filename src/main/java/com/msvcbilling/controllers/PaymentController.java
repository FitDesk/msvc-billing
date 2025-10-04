package com.msvcbilling.controllers;

import com.msvcbilling.dtos.DirectPaymentRequest;
import com.msvcbilling.dtos.PaymentResponse;
import com.msvcbilling.services.PaymentService;
import com.msvcbilling.services.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "API para pagos directos con Mercado Pago")
public class PaymentController {
    private final PaymentService paymentService;
    private final WebhookService webhookService;


    @Operation(summary = "Procesar pago directo")
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody DirectPaymentRequest request) {
        try {
            log.info("Procesando pago directo para referencia: {}", request.externalReference());
            PaymentResponse response = paymentService.processDirectPayment(request);
            return ResponseEntity.ok(response);
        } catch (
                Exception e) {
            log.error("Error procesando pago directo para referencia: {}",
                    request.externalReference(), e);
            throw new RuntimeException("Error al procesar pago: " + e.getMessage());
        }
    }

    @Operation(summary = "Consultar estado de pago")
    @GetMapping("/status/{externalReference}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @PathVariable String externalReference) {
        try {
            log.info("Consultando estado de pago para referencia: {}", externalReference);
            PaymentResponse response = paymentService.getPaymentStatus(externalReference);
            return ResponseEntity.ok(response);
        } catch (
                Exception e) {
            log.error("Error consultando estado de pago para referencia: {}", externalReference, e);
            throw new RuntimeException("Error al consultar estado de pago: " + e.getMessage());
        }
    }

    @Operation(summary = "Obtener métodos de pago disponibles")
    @GetMapping("/methods")
    public ResponseEntity<List<String>> getPaymentMethods() {
        try {
            log.info("Consultando métodos de pago disponibles");
            List<String> methods = paymentService.getPaymentMethods();
            return ResponseEntity.ok(methods);
        } catch (
                Exception e) {
            log.error("Error consultando métodos de pago", e);
            throw new RuntimeException("Error al consultar métodos de pago: " + e.getMessage());
        }
    }

    @Operation(summary = "Webhook de notificaciones de Mercado Pago")
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            HttpServletRequest request,
            @RequestBody String body) {

        try {
            log.info("Webhook recibido de Mercado Pago");
            log.debug("Webhook body: {}", body);

            Map<String, String> headers = extractHeaders(request);
            log.debug("Headers del webhook: {}", headers);

            boolean processed = webhookService.handleWebhook(headers, body);

            if (processed) {
                log.info("Webhook procesado exitosamente");
                return ResponseEntity.ok("OK");
            } else {
                log.info("Webhook ya procesado anteriormente o sin datos válidos");
                return ResponseEntity.ok("ALREADY_PROCESSED");
            }

        } catch (
                Exception e) {
            log.error("Error procesando webhook de Mercado Pago", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    @Operation(summary = "Verificar estado del servicio")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "msvc-billing-checkout-api");
        health.put("mode", "direct_payments");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName.toLowerCase(), request.getHeader(headerName));
        }

        return headers;
    }

}
