package com.msvcbilling.controllers;

import com.msvcbilling.dtos.DirectPaymentRequest;
import com.msvcbilling.dtos.PaymentResponse;
import com.msvcbilling.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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


    @Operation(summary = "Procesar pago directo")
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody DirectPaymentRequest request) {
        try {
            log.info("Procesando pago directo para referencia: {}", request.externalReference());
            PaymentResponse response = paymentService.processDirectPayment(request);
            log.info("✅ Pago procesado exitosamente. Estado: {}", response.status());
            return ResponseEntity.ok(response);
        } catch (
                Exception e) {
            log.error("❌ Error procesando pago para referencia: {}", request.externalReference(), e);
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



}
