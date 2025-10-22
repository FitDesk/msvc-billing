package com.msvcbilling.specification;

import com.msvcbilling.entities.PaymentEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public class PaymentSpecification {

    public static Specification<PaymentEntity> hasStatus(String status) {
        return (root, query, cb) ->
                status == null || status.isEmpty() ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<PaymentEntity> hasPaymentMethod(String paymentMethodId) {
        return (root, query, cb) ->
                paymentMethodId == null || paymentMethodId.isEmpty() ? cb.conjunction() :
                        cb.equal(root.get("paymentMethodId"), paymentMethodId);
    }

    public static Specification<PaymentEntity> isBetweenDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, criteriaBuilder) -> {
            if (startDate != null && endDate != null) {
                return criteriaBuilder.between(root.get("dateApproved"), startDate, endDate);
            } else if (startDate != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("dateApproved"), startDate);
            } else if (endDate != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get("dateApproved"), endDate);
            } else {
                return criteriaBuilder.conjunction();
            }
        };
    }


}
