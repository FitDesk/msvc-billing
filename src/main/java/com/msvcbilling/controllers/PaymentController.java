package com.msvcbilling.controllers;

import com.msvcbilling.annotations.AdminAccess;
import com.msvcbilling.dtos.payment.DirectPaymentRequest;
import com.msvcbilling.dtos.payment.PaymentDetailsResponseDto;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.dtos.payment.PlanUpgradeRequestDto;
import com.msvcbilling.dtos.statistics.DashboardStatisticsResponseDto;
import com.msvcbilling.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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
            log.info("Pago procesado exitosamente. Estado: {}", response.status());
            return ResponseEntity.ok(response);
        } catch (
                Exception e) {
            log.error("Error procesando pago para referencia: {}", request.externalReference(), e);
            throw new RuntimeException("Error al procesar pago: " + e.getMessage());

        }
    }

    @Operation(summary = "Actualizar a un nuevo plan (Upgrade)",
            description = "Calcula el costo prorrateado y procesa el pago por la diferencia para cambiar a un nuevo plan.")
    @PostMapping("/upgrade-plan")
    public ResponseEntity<PaymentResponse> upgradePlan(@Valid @RequestBody PlanUpgradeRequestDto request) {
        try {
            log.info("Iniciando upgrade de plan para el usuario: {}", request.userId());
            PaymentResponse response = paymentService.processPlanUpgrade(request);
            log.info("Upgrade de plan procesado exitosamente. Estado del pago: {}", response.status());
            return ResponseEntity.ok(response);
        } catch (
                Exception e) {
            log.error("Error en el upgrade de plan para el usuario: {}", request.userId(), e);
            throw new RuntimeException("Error al procesar el upgrade de plan: " + e.getMessage());
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


    @PostMapping("/simulate-approval")
    public ResponseEntity<Map<String, String>> simulatePaymentApproval(
            @RequestParam String externalReference,
            @RequestParam String authorizationCode) {
        try {
            paymentService.simulatePaymentApproval(externalReference, authorizationCode);
            return ResponseEntity.ok(Map.of(
                    "message", "Pago simulado como aprobado",
                    "externalReference", externalReference,
                    "authorizationCode", authorizationCode
            ));
        } catch (
                RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/details")
    @Operation(summary = "Listar todos los detalles de pagos con filtros y paginación",
            description = "Obtiene una lista paginada de los detalles de los pagos, permitiendo filtrar por estado, método de pago y rango de fechas.")
    @AdminAccess
    public ResponseEntity<Page<PaymentDetailsResponseDto>> getAllPaymentsDetails(
            @Parameter(description = "Paginación y ordenación. Ejemplo: page=0&size=10&sort=paymentDate,desc") Pageable pageable,
            @Parameter(description = "Filtrar por estado del pago (e.g., approved, pending)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtrar por método de pago (e.g., visa, master)")
            @RequestParam(required = false) String paymentMethodId,
            @Parameter(description = "Fecha de inicio para el filtro (formato ISO: yyyy-MM-dd'T'HH:mm:ss.SSSXXX)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "Fecha de fin para el filtro (formato ISO: yyyy-MM-dd'T'HH:mm:ss.SSSXXX)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {
        Page<PaymentDetailsResponseDto> payments = paymentService.getAllPaymentsDetails(pageable, status, paymentMethodId, startDate, endDate);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/statistics")
    @AdminAccess
    @Operation(summary = "Obtener estadísticas clave para el dashboard",
            description = "Devuelve un resumen de las métricas más importantes, como ingresos mensuales, nuevos miembros y distribución de planes, comparando los resultados con el mes anterior.")
    public ResponseEntity<DashboardStatisticsResponseDto> getDashboardStatistics() {
        DashboardStatisticsResponseDto stats = paymentService.getDashboardStatistics();
        return ResponseEntity.ok(stats);
    }
}
