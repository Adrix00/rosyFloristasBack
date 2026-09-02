package com.floristeriarosy.infrastructure.persistence.entity.inventory;

import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code inventory_alerts} table. {@code save} (insert) never touches an
 * existing row; {@link #applyResolution} is the only mutation, applied to a managed instance
 * loaded by id, mirroring {@code DiscountEntity#applyChanges}.
 */
@Entity
@Table(name = "inventory_alerts")
public class InventoryAlertEntity {

  @Id private UUID id;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "observed_value", nullable = false)
  private int observedValue;

  @Column(name = "expected_value", nullable = false)
  private int expectedValue;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "resolved_by_admin_id")
  private UUID resolvedByAdminId;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "note")
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected InventoryAlertEntity() {}

  /**
   * @param id the primary key
   * @param type which condition was detected
   * @param productId the product it was detected on
   * @param observedValue the observed number
   * @param expectedValue the number it was compared against
   * @param status the current lifecycle state
   * @param resolvedByAdminId who closed it, or {@code null}
   * @param resolvedAt when it was closed, or {@code null}
   * @param note the optional closing note, or {@code null}
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted alert
   */
  public InventoryAlertEntity(
      UUID id,
      String type,
      UUID productId,
      int observedValue,
      int expectedValue,
      String status,
      UUID resolvedByAdminId,
      Instant resolvedAt,
      String note,
      Instant createdAt) {
    this.id = id;
    this.type = type;
    this.productId = productId;
    this.observedValue = observedValue;
    this.expectedValue = expectedValue;
    this.status = status;
    this.resolvedByAdminId = resolvedByAdminId;
    this.resolvedAt = resolvedAt;
    this.note = note;
    this.createdAt = createdAt;
  }

  /**
   * {@code V7} only gives {@code created_at} a DB-side {@code DEFAULT now()}; set it here too,
   * matching {@code DiscountEntity}.
   */
  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /**
   * Copies the resolution fields an already-domain-validated {@link InventoryAlert#resolve} or
   * {@link InventoryAlert#dismiss} produced onto this managed instance.
   *
   * @param domain the domain alert carrying the new resolution fields
   */
  public void applyResolution(InventoryAlert domain) {
    this.status = domain.status().name();
    this.resolvedByAdminId = domain.resolvedByAdminId();
    this.resolvedAt = domain.resolvedAt();
    this.note = domain.note();
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return which condition was detected
   */
  public String getType() {
    return type;
  }

  /**
   * @return the product it was detected on
   */
  public UUID getProductId() {
    return productId;
  }

  /**
   * @return the observed number
   */
  public int getObservedValue() {
    return observedValue;
  }

  /**
   * @return the number it was compared against
   */
  public int getExpectedValue() {
    return expectedValue;
  }

  /**
   * @return the current lifecycle state
   */
  public String getStatus() {
    return status;
  }

  /**
   * @return who closed it, or {@code null}
   */
  public UUID getResolvedByAdminId() {
    return resolvedByAdminId;
  }

  /**
   * @return when it was closed, or {@code null}
   */
  public Instant getResolvedAt() {
    return resolvedAt;
  }

  /**
   * @return the optional closing note, or {@code null}
   */
  public String getNote() {
    return note;
  }

  /**
   * @return when the row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
