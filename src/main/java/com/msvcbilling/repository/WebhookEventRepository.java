package com.msvcbilling.repository;

import com.msvcbilling.entities.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, String> {

    boolean existsById(String id);
}
