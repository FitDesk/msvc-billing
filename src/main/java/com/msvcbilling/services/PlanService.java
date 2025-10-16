package com.msvcbilling.services;

import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.ImageUploadResponseDto;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.dtos.UpdatePlanRequestDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PlanService {
    List<PlanResponseDto> getAllActivePlans();
    List<PlanResponseDto> getAllPlans();
    PlanResponseDto getPlanById(UUID id);
    PlanResponseDto createPlan(CreatePlanRequestDto request,MultipartFile planImage);
    PlanResponseDto updatePlan(UUID id, UpdatePlanRequestDto request, MultipartFile file);
    void deletePlan(UUID id);
    boolean deletePlanImage(UUID planId);
    ImageUploadResponseDto updateProfileImage(UUID planId, MultipartFile file);
}