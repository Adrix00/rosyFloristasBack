package com.floristeriarosy.infrastructure.persistence.adapter.shared;

import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.infrastructure.persistence.entity.shared.AuditLogEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.shared.repository.AuditLogJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link AuditLogPort} with plain JPA insert (ADR-002, ADR-010). */
@Repository
public class AuditLogPersistenceAdapter implements AuditLogPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogPersistenceAdapter.class);

  private final AuditLogJpaRepository jpaRepository;

  /**
   * @param jpaRepository inserts the audit row
   */
  public AuditLogPersistenceAdapter(AuditLogJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  /**
   * @param adminUserId the administrator who performed the action
   * @param action what kind of action it was
   * @param entityType the entity type acted upon
   * @param entityId the entity acted upon
   * @param changedFields the names of the fields that changed, never their values
   */
  @Override
  public void record(
      UUID adminUserId,
      AuditAction action,
      String entityType,
      UUID entityId,
      List<String> changedFields) {
    LOGGER.debug(
        "record adminUserId={} action={} entityType={} entityId={} changedFields={}",
        adminUserId,
        action,
        entityType,
        entityId,
        changedFields);
    AuditLogEntity entity =
        new AuditLogEntity(
            UUID.randomUUID(),
            adminUserId,
            action,
            entityType,
            entityId,
            changedFields.toArray(new String[0]),
            Instant.now());
    jpaRepository.save(entity);
    LOGGER.debug("record -> id={} saved", entity.getId());
  }
}
