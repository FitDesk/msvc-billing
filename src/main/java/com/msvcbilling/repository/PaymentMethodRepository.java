package com.msvcbilling.repository;

import com.msvcbilling.entities.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, UUID> {

    List<PaymentMethodEntity> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<PaymentMethodEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentMethodEntity> findByUserIdAndIsDefaultTrue(UUID userId);

    boolean existsByUserIdAndCardToken(UUID userId, String cardToken);

    @Modifying
    @Query("UPDATE PaymentMethodEntity pm SET pm.isDefault = false WHERE pm.userId = :userId AND pm.id != :excludeId")
    void unsetDefaultForUser(@Param("userId") UUID userId, @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(pm) FROM PaymentMethodEntity pm WHERE pm.userId = :userId AND pm.isActive = true")
    long countActiveCardsByUser(@Param("userId") UUID userId);
}