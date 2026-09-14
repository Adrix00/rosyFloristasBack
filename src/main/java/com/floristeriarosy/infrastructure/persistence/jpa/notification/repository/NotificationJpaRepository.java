package com.floristeriarosy.infrastructure.persistence.jpa.notification.repository;

import com.floristeriarosy.infrastructure.persistence.entity.notification.NotificationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA access to the {@code notifications} transactional outbox. */
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {}
