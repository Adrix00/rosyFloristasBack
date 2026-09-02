package com.floristeriarosy.infrastructure.persistence.entity.shared;

import com.floristeriarosy.application.shared.dto.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping of the {@code audit_log} table (ADR-010). {@code changes} is mapped for
 * completeness with the table shape, but every writer today leaves it {@code null}: no entity
 * type used so far is on {@code chk_audit_log_changes_pii_free}'s allow-list.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

  @Id private UUID id;

  @Column(name = "admin_user_id")
  private UUID adminUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AuditAction action;

  @Column(name = "entity_type", nullable = false, length = 60)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "changed_fields", nullable = false)
  private String[] changedFields;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column private String changes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected AuditLogEntity() {}

  /**
   * @param id the primary key
   * @param adminUserId the administrator who performed the action, or {@code null}
   * @param action what kind of action it was
   * @param entityType the entity type acted upon
   * @param entityId the entity acted upon, or {@code null}
   * @param changedFields the names of the fields that changed
   * @param createdAt when the action happened
   */
  public AuditLogEntity(
      UUID id,
      UUID adminUserId,
      AuditAction action,
      String entityType,
      UUID entityId,
      String[] changedFields,
      Instant createdAt) {
    this.id = id;
    this.adminUserId = adminUserId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.changedFields = changedFields.clone();
    this.changes = null;
    this.createdAt = createdAt;
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the administrator who performed the action, or {@code null}
   */
  public UUID getAdminUserId() {
    return adminUserId;
  }

  /**
   * @return what kind of action it was
   */
  public AuditAction getAction() {
    return action;
  }

  /**
   * @return the entity type acted upon
   */
  public String getEntityType() {
    return entityType;
  }

  /**
   * @return the entity acted upon, or {@code null}
   */
  public UUID getEntityId() {
    return entityId;
  }

  /**
   * @return the names of the fields that changed
   */
  public String[] getChangedFields() {
    return changedFields.clone();
  }

  /**
   * @return the before/after values, always {@code null} for a PII-bearing entity type
   */
  public String getChanges() {
    return changes;
  }

  /**
   * @return when the action happened
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
