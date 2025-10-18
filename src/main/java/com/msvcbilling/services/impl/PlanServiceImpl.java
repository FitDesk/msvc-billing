package com.msvcbilling.services.impl;

import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.ImageUploadResponseDto;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.dtos.UpdatePlanRequestDto;
import com.msvcbilling.entities.PlanEntity;
import com.msvcbilling.exceptions.PlanNotFoundException;
import com.msvcbilling.exceptions.PlansNotFoundException;
import com.msvcbilling.mappers.PlanMapper;
import com.msvcbilling.repository.PlanRepository;
import com.msvcbilling.services.CloudinaryService;
import com.msvcbilling.services.PlanService;

import static com.msvcbilling.utils.NPEUtil.*;

import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "planService", fallbackMethod = "getAllActivePlansFallback")
    @Retry(name = "databaseRetry")
    public List<PlanResponseDto> getAllActivePlans() {
        log.info("Obteniendo todos los planes activos");
        return planRepository.findActivePlansOrderedByPopularityAndPrice()
                .stream()
                .map(planMapper::entityToDto)
                .toList();
    }

    public List<PlanResponseDto> getAllActivePlansFallback(Throwable ex) {
        log.error("Error al obtener planes activos , ejecutando fallback: {}", ex.getMessage());
        throw new PlansNotFoundException("No se encontraron planes activos");
    }


    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "planService", fallbackMethod = "getAllPlansFallback")
    @Retry(name = "databaseRetry")
    public List<PlanResponseDto> getAllPlans() {
        log.info("Obteniendo todos los planes");
        return planRepository.findAll()
                .stream()
                .map(planMapper::entityToDto)
                .toList();
    }

    public List<PlanResponseDto> getAllPlansFallback(Throwable ex) {
        log.error("No se pudieron listar los planes, ejecutando el fallback {}", ex.getMessage());
        throw new PlansNotFoundException("No se pudieron listar los planes {}");
    }


    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "planService", fallbackMethod = "getPlanByIdFallback")
    @Retry(name = "databaseRetry")
    public PlanResponseDto getPlanById(UUID id) {
        log.info("Buscando plan con ID: {}", id);
        PlanEntity plan = planRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado con ID: " + id));
        return planMapper.entityToDto(plan);
    }

    public PlanResponseDto getPlanById(UUID id, Throwable ex) {
        log.error("No se pudo traer el plan con ID : {} , ejecutando fallback {}", id.toString(), ex.getMessage());
        throw new PlanNotFoundException(id);
    }


    @Override
    @Transactional
    public PlanResponseDto createPlan(CreatePlanRequestDto request, MultipartFile planImage) {
        log.info("Creando nuevo plan: {}", request.name());

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

        if (planImage != null && !planImage.isEmpty()) {
            ImageUploadResponseDto uploadResponse = cloudinaryService.uploadProfileImage(planImage, plan.getId());
            plan.setPlanImageUrl(uploadResponse.getUrl());
        }

        PlanEntity savedPlan = planRepository.save(plan);
        log.info("Plan creado exitosamente con ID: {}", savedPlan.getId());

        return planMapper.entityToDto(savedPlan);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "planService", fallbackMethod = "updatePlanFallback")
    @Retry(name = "databaseRetry")
    public PlanResponseDto updatePlan(UUID id, UpdatePlanRequestDto request, MultipartFile file) {
        log.info("Actualizando plan con ID: {}", id);

        PlanEntity existingPlan = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException(id));
        boolean updated = false;

        if (request != null) {
            updated |= applyIfNotBlank(request.name(), existingPlan::setName);
            updated |= applyIfNotBlank(request.description(), existingPlan::setDescription);
            updated |= applyIfNotNull(request.price(), existingPlan::setPrice);
            updated |= applyIfNotNull(request.durationMonths(), existingPlan::setDurationMonths);
            updated |= applyIfNotBlank(request.currency(), existingPlan::setCurrency);

            if (request.isActive() != null) {
                existingPlan.setIsActive(request.isActive());
                updated = true;
            }
            if (request.isPopular() != null) {
                existingPlan.setIsPopular(request.isPopular());
                updated = true;
            }
            updated |= applyIfNotNull(request.features(), existingPlan::setFeatures);
        } else {
            log.info("No se recibió DTO de plan; solo se procesará la imagen si se proporciona.");
        }

        if (file != null && !file.isEmpty()) {
            String oldPublicId = cloudinaryService.extractPublicIdFromUrl(existingPlan.getPlanImageUrl());
            ImageUploadResponseDto uploadResponse = cloudinaryService.updateProfileImage(file, id, oldPublicId);
            existingPlan.setPlanImageUrl(uploadResponse.getUrl());
            log.info("Imagen de membresia actualizada");
            updated = true;
        }

        if (!updated) {
            throw new IllegalArgumentException("No se proporcionaron datos para actualizar (ni DTO válido ni archivo).");
        }
        PlanEntity updatedPlan = planRepository.save(existingPlan);
        log.info("Plan actualizado exitosamente");

        return planMapper.entityToDto(updatedPlan);
    }

    public PlanResponseDto updatePlanFallback(UUID id, Throwable ex) {
        log.error("Error al actualizar el plan con ID: {} , ejecutando fallback: {}", id, ex.getMessage());
        throw new RuntimeException("No se pudo actualizar el plan con ID: " + id + "debido a un error");
    }

    @Override
    @Transactional
    public void deletePlan(UUID id) {
        log.info("Eliminando plan con ID: {}", id);

        if (!planRepository.existsById(id)) {
            throw new EntityNotFoundException("Plan no encontrado con ID: " + id);
        }

        planRepository.deleteById(id);
        log.info(" Plan eliminado exitosamente");
    }

    @Override
    @Transactional
    public boolean deletePlanImage(UUID planId) {
        log.info("Eliminando imagen de membrecia con id {}", planId);
        PlanEntity plan = planRepository.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));

        String currentImageUrl = plan.getPlanImageUrl();

        String publicId = cloudinaryService.extractPublicIdFromUrl(currentImageUrl);
        boolean deleted = false;

        if (publicId != null) {
            deleted = cloudinaryService.deleteImage(publicId);
        }
        plan.setPlanImageUrl(null);
        planRepository.save(plan);
        log.info("Imagen de membresia eliminada exitosamente");
        return deleted;
    }

    @Override
    @Transactional
    public ImageUploadResponseDto updateProfileImage(UUID planId, MultipartFile file) {
        log.info("Actualizando solo la foto de perfil para membresia con id {}", planId);

        PlanEntity plan = planRepository.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));

        String oldPublicId = cloudinaryService.extractPublicIdFromUrl(plan.getPlanImageUrl());
        ImageUploadResponseDto uploadResponse = cloudinaryService.updateProfileImage(file, planId, oldPublicId);

        plan.setPlanImageUrl(uploadResponse.getUrl());
        planRepository.save(plan);
        return uploadResponse;
    }
}