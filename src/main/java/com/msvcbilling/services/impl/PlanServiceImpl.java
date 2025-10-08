package com.msvcbilling.services.impl;

import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.entities.PlanEntity;
import com.msvcbilling.mappers.PlanMapper;
import com.msvcbilling.repository.PlanRepository;
import com.msvcbilling.services.PlanService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponseDto> getAllActivePlans() {
        log.info("📋 Obteniendo todos los planes activos");
        return planRepository.findActivePlansOrderedByPopularityAndPrice()
                .stream()
                .map(planMapper::entityToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponseDto> getAllPlans() {
        log.info("📋 Obteniendo todos los planes");
        return planRepository.findAll()
                .stream()
                .map(planMapper::entityToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponseDto getPlanById(UUID id) {
        log.info("🔍 Buscando plan con ID: {}", id);
        PlanEntity plan = planRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado con ID: " + id));
        return planMapper.entityToDto(plan);
    }

    @Override
    @Transactional
    public PlanResponseDto createPlan(CreatePlanRequestDto request) {
        log.info("➕ Creando nuevo plan: {}", request.name());

        PlanEntity plan = PlanEntity.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .durationMonths(request.durationMonths())
                .currency(request.currency() != null ? request.currency() : "PEN")
                .isActive(request.isActive() != null ? request.isActive() : true)
                .isPopular(request.isPopular() != null ? request.isPopular() : false)
                .features(request.features())
                .build();

        PlanEntity savedPlan = planRepository.save(plan);
        log.info("✅ Plan creado exitosamente con ID: {}", savedPlan.getId());

        return planMapper.entityToDto(savedPlan);
    }

    @Override
    @Transactional
    public PlanResponseDto updatePlan(UUID id, CreatePlanRequestDto request) {
        log.info("✏️ Actualizando plan con ID: {}", id);

        PlanEntity existingPlan = planRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado con ID: " + id));

        existingPlan.setName(request.name());
        existingPlan.setDescription(request.description());
        existingPlan.setPrice(request.price());
        existingPlan.setDurationMonths(request.durationMonths());
        existingPlan.setCurrency(request.currency() != null ? request.currency() : "PEN");
        existingPlan.setIsActive(request.isActive() != null ? request.isActive() : true);
        existingPlan.setIsPopular(request.isPopular() != null ? request.isPopular() : false);
        existingPlan.setFeatures(request.features());

        PlanEntity updatedPlan = planRepository.save(existingPlan);
        log.info("✅ Plan actualizado exitosamente");

        return planMapper.entityToDto(updatedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(UUID id) {
        log.info("🗑️ Eliminando plan con ID: {}", id);

        if (!planRepository.existsById(id)) {
            throw new EntityNotFoundException("Plan no encontrado con ID: " + id);
        }

        planRepository.deleteById(id);
        log.info("✅ Plan eliminado exitosamente");
    }
}