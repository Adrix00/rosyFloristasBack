package com.floristeriarosy.infrastructure.persistence.adapter.notification;

import com.floristeriarosy.application.notification.port.out.NotificationWritePort;
import com.floristeriarosy.infrastructure.persistence.entity.notification.NotificationEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.notification.repository.NotificationJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.notification.NotificationPersistenceMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link NotificationWritePort} with plain JPA (ADR-002, notification.md section 2/3.4).
 */
@Repository
public class NotificationPersistenceAdapter implements NotificationWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationPersistenceAdapter.class);

  private final NotificationJpaRepository jpaRepository;
  private final NotificationPersistenceMapper mapper;

  /**
   * @param jpaRepository writes the outbox row
   * @param mapper builds the {@link NotificationEntity}
   */
  public NotificationPersistenceAdapter(
      NotificationJpaRepository jpaRepository, NotificationPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param type the notification type
   * @param orderId the referenced order, or {@code null}
   * @param customerId the referenced customer, or {@code null}
   */
  @Override
  public void insert(String type, UUID orderId, UUID customerId) {
    LOGGER.debug("insert type={} orderId={} customerId={}", type, orderId, customerId);
    NotificationEntity entity = mapper.toEntity(type, orderId, customerId, UUID.randomUUID(), Instant.now());
    jpaRepository.save(entity);
    LOGGER.debug("insert type={} -> saved", type);
  }
}
