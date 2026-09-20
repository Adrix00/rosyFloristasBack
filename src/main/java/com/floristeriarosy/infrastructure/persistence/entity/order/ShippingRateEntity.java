package com.floristeriarosy.infrastructure.persistence.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of {@code shipping_rates}. No {@code version} column, deliberately: a handful of rows
 * and one change at a time, serialized by locking them all (order.md §4; ADR-009 does not list this
 * table).
 */
@Entity
@Table(name = "shipping_rates")
public class ShippingRateEntity {

  @Id private UUID id;

  @Column(name = "min_km", nullable = false, precision = 5, scale = 2)
  private BigDecimal minKm;

  @Column(name = "max_km", nullable = false, precision = 5, scale = 2)
  private BigDecimal maxKm;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "free_from_amount", precision = 10, scale = 2)
  private BigDecimal freeFromAmount;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected ShippingRateEntity() {}

  /**
   * @param id the primary key
   * @param minKm where the band starts
   * @param maxKm where the band ends
   * @param price the shipping cost
   * @param freeFromAmount the free-shipping threshold, or {@code null}
   * @param active whether the band is offered
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted band
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted band
   */
  public ShippingRateEntity(
      UUID id,
      BigDecimal minKm,
      BigDecimal maxKm,
      BigDecimal price,
      BigDecimal freeFromAmount,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.minKm = minKm;
    this.maxKm = maxKm;
    this.price = price;
    this.freeFromAmount = freeFromAmount;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Updates the editable columns of a managed row in place, so Hibernate issues an {@code UPDATE}
   * of the row it loaded instead of merging a detached copy.
   *
   * @param minKm the new start
   * @param maxKm the new end
   * @param price the new price
   * @param freeFromAmount the new free-shipping threshold, or {@code null}
   * @param active the new flag
   */
  public void applyChanges(
      BigDecimal minKm, BigDecimal maxKm, BigDecimal price, BigDecimal freeFromAmount, boolean active) {
    this.minKm = minKm;
    this.maxKm = maxKm;
    this.price = price;
    this.freeFromAmount = freeFromAmount;
    this.active = active;
  }

  /** Sets the timestamps in application code; {@code created_at} also has a database default. */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  /** Refreshes {@code updatedAt} on every update. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return where the band starts */
  public BigDecimal getMinKm() {
    return minKm;
  }

  /** @return where the band ends */
  public BigDecimal getMaxKm() {
    return maxKm;
  }

  /** @return the shipping cost */
  public BigDecimal getPrice() {
    return price;
  }

  /** @return the free-shipping threshold, or {@code null} */
  public BigDecimal getFreeFromAmount() {
    return freeFromAmount;
  }

  /** @return whether the band is offered */
  public boolean isActive() {
    return active;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
