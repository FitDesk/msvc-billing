package com.msvcbilling.config.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.Instant;

public class AuditListener {

    @PrePersist
    public void onPrePersist(Object entity) {
        applyAudit(entity, true);
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        applyAudit(entity, false);
    }

    private void applyAudit(Object entity, boolean isCreate) {
        try {
            Field f = findAuditField(entity);
            if (f == null)
                return;
            f.setAccessible(true);

            Audit audit = (Audit) f.get(entity);
            if (audit == null)
                audit = Audit.builder().build();

            String user = currentUsername();
            Instant now = Instant.now();

            if (isCreate) {
                if (audit.getCreatedAt() == null)
                    audit.setCreatedAt(now);
                if (audit.getCreatedBy() == null)
                    audit.setCreatedBy(user);
            }
            audit.setUpdatedAt(now);
            audit.setUpdatedBy(user);

            f.set(entity, audit);
        } catch (
                Exception ignored) {
        }
    }

    private Field findAuditField(Object entity) {
        Class<?> cls = entity.getClass();
        while (cls != null && cls != Object.class) {
            try {
                return cls.getDeclaredField("audit");
            } catch (
                    NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null)
                return auth.getName();
        } catch (
                Exception ignored) {
        }
        return "system";
    }
}