package com.msvcbilling.repository;

import com.msvcbilling.entities.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {
    List<PlanEntity> findByIsActiveTrue();

    @Query("SELECT p FROM PlanEntity p WHERE p.isActive = true ORDER BY p.isPopular DESC, p.price ASC")
    List<PlanEntity> findActivePlansOrderedByPopularityAndPrice();
}