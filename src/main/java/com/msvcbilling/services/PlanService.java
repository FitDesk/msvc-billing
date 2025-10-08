package com.msvcbilling.services;

import com.msvcbilling.dtos.CreatePlanRequestDto;
import com.msvcbilling.dtos.PlanResponseDto;

import java.util.List;
import java.util.UUID;

public interface PlanService {
    List<PlanResponseDto> getAllActivePlans();
    List<PlanResponseDto> getAllPlans();
    PlanResponseDto getPlanById(UUID id);
    PlanResponseDto createPlan(CreatePlanRequestDto request);
    PlanResponseDto updatePlan(UUID id, CreatePlanRequestDto request);
    void deletePlan(UUID id);
}