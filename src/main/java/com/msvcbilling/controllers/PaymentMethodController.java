package com.msvcbilling.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.msvcbilling.dtos.card.CreatePaymentMethodRequest;
import com.msvcbilling.dtos.card.PaymentWithSavedCardRequest;
import com.msvcbilling.dtos.card.SavedPaymentMethodDto;
import com.msvcbilling.dtos.card.UpdatePaymentMethodRequest;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.services.AuthorizationService;
import com.msvcbilling.services.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("payment-methods")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Methods", description = "Gestión de métodos de pago guardados")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final AuthorizationService authorizationService;

    @Operation(summary = "Guardar un nuevo método de pago")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedPaymentMethodDto> savePaymentMethod(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        UUID userId = authorizationService.getUserId(authentication);
        SavedPaymentMethodDto saved = paymentMethodService.savePaymentMethod(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "Obtener todos los métodos de pago del usuario")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SavedPaymentMethodDto>> getUserPaymentMethods(Authentication authentication) {
        UUID userId = authorizationService.getUserId(authentication);
        return ResponseEntity.ok(paymentMethodService.getUserPaymentMethods(userId));
    }

    @Operation(summary = "Obtener un método de pago específico")
    @GetMapping("/{cardId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedPaymentMethodDto> getPaymentMethod(
            Authentication authentication,
            @PathVariable UUID cardId) {
        UUID userId = authorizationService.getUserId(authentication);
        return ResponseEntity.ok(paymentMethodService.getPaymentMethod(userId, cardId));
    }

    @Operation(summary = "Actualizar un método de pago")
    @PatchMapping("/{cardId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedPaymentMethodDto> updatePaymentMethod(
            Authentication authentication,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        UUID userId = authorizationService.getUserId(authentication);
        return ResponseEntity.ok(paymentMethodService.updatePaymentMethod(userId, cardId, request));
    }

    @Operation(summary = "Eliminar un método de pago")
    @DeleteMapping("/{cardId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePaymentMethod(
            Authentication authentication,
            @PathVariable UUID cardId) {
        UUID userId = authorizationService.getUserId(authentication);
        paymentMethodService.deletePaymentMethod(userId, cardId);
    }

    @Operation(summary = "Procesar pago con tarjeta guardada")
    @PostMapping("/process-payment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> processPaymentWithSavedCard(
            Authentication authentication,
            @Valid @RequestBody PaymentWithSavedCardRequest request) {

        try {
            UUID userId = authorizationService.getUserId(authentication);
            if (!userId.equals(request.userId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("El ID de usuario no coincide con la autenticación.");
            }

            PaymentResponse response = paymentMethodService.processPaymentWithSavedCard(request);
            return ResponseEntity.ok(response);

        } catch (
                MPApiException e) {
            log.error("Error de API de Mercado Pago: {}", e.getApiResponse().getContent(), e);
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(e.getApiResponse().getContent());

        } catch (
                Exception e) {
            log.error("Error genérico procesando pago con tarjeta guardada", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno al procesar el pago: " + e.getMessage());
        }
    }
}