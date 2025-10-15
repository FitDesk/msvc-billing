package com.msvcbilling.controllers;


import com.msvcbilling.annotations.AdminAccess;
import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.ImageUploadResponseDto;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.services.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Plans", description = "API para gestión de planes de suscripción")
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "Obtener todos los planes activos")
    @GetMapping("/active")
    public ResponseEntity<List<PlanResponseDto>> getActivePlans() {
        return ResponseEntity.ok(planService.getAllActivePlans());
    }

    @Operation(summary = "Obtener todos los planes")
    @GetMapping
    public ResponseEntity<List<PlanResponseDto>> getAllPlans() {
        return ResponseEntity.ok(planService.getAllPlans());
    }

    @Operation(summary = "Obtener plan por ID")
    @GetMapping("/{id}")
    public ResponseEntity<PlanResponseDto> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    @Operation(summary = "Crear nuevo plan")
    @PostMapping
    @AdminAccess
    public ResponseEntity<PlanResponseDto> createPlan(
            @Valid @RequestPart(value = "plan") CreatePlanRequestDto request,
            @RequestPart(value = "billingImage") MultipartFile file
    ) {
        log.info("Creando nuevo plan: {}", request.name());
        PlanResponseDto plan = planService.createPlan(request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @Operation(summary = "Actualizar plan existente")
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAccess
    public ResponseEntity<PlanResponseDto> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestPart(value = "planReq", required = false) CreatePlanRequestDto planReq,
            @RequestPart(value = "billingImage", required = false) MultipartFile billingImage
    ) {
        log.info("Actualizando plan con ID: {}", id);
        PlanResponseDto plan = planService.updatePlan(id, planReq, billingImage);
        return ResponseEntity.ok(plan);
    }

    @Operation(summary = "Eliminar plan")
    @DeleteMapping("/{id}")
    @AdminAccess
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{planId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponseDto> updateProfileMembresia(
            @PathVariable UUID planId,
            @RequestPart("file") MultipartFile file
    ) {
        ImageUploadResponseDto response = planService.updateProfileImage(planId, file);
        return ResponseEntity.ok(response);
    }

    @AdminAccess
    @DeleteMapping("/{planId}/image")
    public ResponseEntity<Void> deleteProfileImage(@PathVariable UUID planId) {
        boolean deleted = planService.deletePlanImage(planId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
    }

}