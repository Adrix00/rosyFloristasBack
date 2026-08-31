package com.floristeriarosy.infrastructure.persistence.entity.attribute;

import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code product_attribute_definitions} table. No {@code version} column,
 * deliberately: excluded from ADR-009's optimistic-locking list, same reasoning as {@code categories}.
 */
@Entity
@Table(name = "product_attribute_definitions")
public class AttributeDefinitionEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDefinitionEntity.class);

  @Id private UUID id;

  @Column(name = "attribute_key", nullable = false, length = 100)
  private String attributeKey;

  @Column(nullable = false, length = 150)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false, length = 20)
  private AttributeDataType dataType;

  @Column(nullable = false)
  private boolean filterable;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected AttributeDefinitionEntity() {}

  /**
   * @param id the primary key
   * @param attributeKey the declared key
   * @param label the visible label
   * @param dataType the declared value type
   * @param filterable whether {@code GET /products} may filter by this key
   * @param position position in the admin's attribute list
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted definition
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted
   *     definition
   */
  public AttributeDefinitionEntity(
      UUID id,
      String attributeKey,
      String label,
      AttributeDataType dataType,
      boolean filterable,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.attributeKey = attributeKey;
    this.label = label;
    this.dataType = dataType;
    this.filterable = filterable;
    this.position = position;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Sets {@code createdAt}/{@code updatedAt} in application code, since {@code V1} only gives
   * {@code created_at} a DB-side {@code DEFAULT now()} and nothing updates {@code updated_at} on
   * its own.
   */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    LOGGER.debug("onCreate id={} createdAt={}", id, createdAt);
  }

  /** Refreshes {@code updatedAt} on every update; see {@link #onCreate()}. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
    LOGGER.debug("onUpdate id={} updatedAt={}", id, updatedAt);
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the declared key
   */
  public String getAttributeKey() {
    return attributeKey;
  }

  /**
   * @return the visible label
   */
  public String getLabel() {
    return label;
  }

  /**
   * @return the declared value type
   */
  public AttributeDataType getDataType() {
    return dataType;
  }

  /**
   * @return whether {@code GET /products} may filter by this key
   */
  public boolean isFilterable() {
    return filterable;
  }

  /**
   * @return position in the admin's attribute list
   */
  public int getPosition() {
    return position;
  }

  /**
   * @return when the row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
