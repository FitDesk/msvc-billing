package com.msvcbilling.repository;

import com.msvcbilling.dtos.statistics.PlanDistributionDto;
import com.msvcbilling.dtos.statistics.StatusDistributionDto;
import com.msvcbilling.entities.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {
    Optional<PaymentEntity> findByExternalReference(String externalReference);
    Optional<PaymentEntity> findByPaymentId(Long paymentId);
    List<PaymentEntity> findByStatus(String status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEntity p WHERE p.status = 'approved' AND p.dateApproved BETWEEN :startDate AND :endDate")
    BigDecimal findTotalRevenueBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.status = 'approved' AND p.dateApproved BETWEEN :startDate AND :endDate")
    long countNewMembersBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT new com.msvcbilling.dtos.statistics.PlanDistributionDto(p.plan.name, COUNT(p)) FROM PaymentEntity p WHERE p.status = 'approved' GROUP BY p.plan.name ORDER BY COUNT(p) DESC")
    List<PlanDistributionDto> findTopPlans();

    @Query("SELECT new com.msvcbilling.dtos.statistics.StatusDistributionDto(p.status, COUNT(p)) FROM PaymentEntity p GROUP BY p.status")
    List<StatusDistributionDto> findPaymentStatusDistribution();

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.status = 'approved'")
    long countTotalApprovedPayments();
    Optional<PaymentEntity> findFirstByUserIdAndStatusOrderByDateApprovedDesc(UUID userId, String status);
}
