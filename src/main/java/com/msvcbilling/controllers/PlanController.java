package com.msvcbilling.controllers;


import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.services.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<PlanResponseDto> createPlan(@Valid @RequestBody CreatePlanRequestDto request) {
        log.info("Creando nuevo plan: {}", request.name());
        PlanResponseDto plan = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @Operation(summary = "Actualizar plan existente")
    @PutMapping("/{id}")
    public ResponseEntity<PlanResponseDto> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePlanRequestDto request) {
        log.info("Actualizando plan con ID: {}", id);
        PlanResponseDto plan = planService.updatePlan(id, request);
        return ResponseEntity.ok(plan);
    }

    @Operation(summary = "Eliminar plan")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}