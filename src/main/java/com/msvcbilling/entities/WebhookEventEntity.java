package com.msvcbilling.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventEntity {

    @Id
    private String id;

    @Column(name = "topic")
    private String topic;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "raw_body", columnDefinition = "TEXT")
    private String rawBody;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = OffsetDateTime.now();
        }
    }
}