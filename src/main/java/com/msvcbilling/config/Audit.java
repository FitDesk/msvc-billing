package com.msvcbilling.config;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

@Embeddable
public class Audit {
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
    @PreUpdate
    void preUpdate() {
        updateAt = LocalDateTime.now();
    }
}