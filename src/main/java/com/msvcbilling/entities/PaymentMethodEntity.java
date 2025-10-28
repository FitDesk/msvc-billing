package com.msvcbilling.entities;


import com.msvcbilling.config.audit.Audit;
import com.msvcbilling.config.audit.AuditListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "saved_payment_methods",
        indexes = {
                @Index(name = "idx_user_payment_methods", columnList = "user_id"),
                @Index(name = "idx_card_token", columnList = "card_token")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
public class PaymentMethodEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "card_token", nullable = false)
    private String cardToken;

    @Column(name = "last_four_digits", nullable = false, length = 4)
    private String lastFourDigits;

    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;

    @Column(name = "card_brand", nullable = false)
    private String cardBrand;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "expiration_month", nullable = false)
    private Integer expirationMonth;

    @Column(name = "expiration_year", nullable = false)
    private Integer expirationYear;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "billing_address")
    private String billingAddress;

    @Column(name = "issuer_bank")
    private String issuerBank;

    @Embedded
    private Audit audit;

    @Transient
    public boolean isExpired() {
        LocalDate now = LocalDate.now();
        LocalDate expiration = LocalDate.of(expirationYear, expirationMonth, 1)
                .withDayOfMonth(1).plusMonths(1).minusDays(1);
        return now.isAfter(expiration);
    }
}