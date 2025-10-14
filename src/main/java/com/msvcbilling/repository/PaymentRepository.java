package com.msvcbilling.repository;

import com.msvcbilling.entities.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByExternalReference(String externalReference);
    Optional<PaymentEntity> findByPaymentId(Long paymentId);
    List<PaymentEntity> findByStatus(String status);
}
