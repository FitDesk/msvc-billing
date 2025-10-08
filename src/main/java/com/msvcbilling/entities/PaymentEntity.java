package com.msvcbilling.entities;

import com.msvcbilling.config.audit.Audit;
import jakarta.persistence.*;
import jakarta.persistence.Id;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", referencedColumnName = "id")
    private PlanEntity plan;

    @Column(name = "external_reference", unique = true)
    private String externalReference;

    @Column(name = "payment_id", unique = true)
    private Long paymentId;

    @Column(name = "token")
    private String token;

    @Column(name = "payment_method_id")
    private String paymentMethodId;

    @Column(name = "payment_type_id")
    private String paymentTypeId;

    @Column(name = "installments")
    private Integer installments;

    @Column(name = "authorization_code")
    private String authorizationCode;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_id")
    private String currencyId;

    @Column(name = "status")
    private String status;

    @Column(name = "status_detail")
    private String statusDetail;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "payer_first_name")
    private String payerFirstName;

    @Column(name = "payer_last_name")
    private String payerLastName;

    @Column(name = "payer_identification_type")
    private String payerIdentificationType;

    @Column(name = "payer_identification_number")
    private String payerIdentificationNumber;

    @Column(name = "date_created")
    private OffsetDateTime dateCreated;

    @Column(name = "date_approved")
    private OffsetDateTime dateApproved;

    @Embedded
    private Audit audit;

    @PrePersist
    protected void onCreate() {
        if (dateCreated == null) {
            dateCreated = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if ("approved".equals(status) && dateApproved == null) {
            dateApproved = OffsetDateTime.now();
        }
    }
}