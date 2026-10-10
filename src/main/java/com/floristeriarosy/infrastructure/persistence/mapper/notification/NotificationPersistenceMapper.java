package com.floristeriarosy.infrastructure.persistence.mapper.notification;

import com.floristeriarosy.infrastructure.persistence.entity.notification.NotificationEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** JPA entity factory for the transactional outbox (notification.md, section 2/3.4). */
@Component
public class NotificationPersistenceMapper {

  /**
   * @param type the notification catalog type
   * @param orderId the referenced order, or {@code null}
   * @param customerId the referenced customer, or {@code null}
   * @param id the primary key to assign
   * @param now the creation / first-attempt timestamp
   * @return a new {@code PENDING} outbox row
   */
  public NotificationEntity toEntity(String type, UUID orderId, UUID customerId, UUID id, Instant now) {
    return new NotificationEntity(
        id, type, orderId, customerId, "PENDING", 0, now, null, null, now);
  }
}
