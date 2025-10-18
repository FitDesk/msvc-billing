package com.msvcbilling.entities;


import com.msvcbilling.config.audit.Audit;
import com.msvcbilling.config.audit.AuditListener;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditListener.class)
public class PlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "currency", nullable = false)
    private String currency = "PEN";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_popular")
    private Boolean isPopular = false;

    @Column(name = "plan_image_url")
    private String planImageUrl;

    @ElementCollection
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "feature")
    private List<String> features;

    @Embedded
    private Audit audit;
}