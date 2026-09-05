package com.floristeriarosy.infrastructure.persistence.entity.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code carts} table. Carries {@code @Version} (ADR-009 amendment,
 * 2026-09-06): a signed-in customer's own cart is reachable from two devices at once, so two
 * concurrent writes to the same row are plausible, unlike the "one admin, one form" shape the
 * ADR's original aggregates assume.
 */
@Entity
@Table(name = "carts")
public class CartEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartEntity.class);

  @Id private UUID id;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "session_token", nullable = false, length = 255)
  private String sessionToken;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected CartEntity() {}

  /**
   * @param id the primary key
   * @param customerId the owning customer, or {@code null} for a guest cart
   * @param sessionToken the token identifying this cart while there is no session
   * @param expiresAt when this cart expires
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted cart
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted cart
   */
  public CartEntity(
      UUID id, UUID customerId, String sessionToken, Instant expiresAt, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.customerId = customerId;
    this.sessionToken = sessionToken;
    this.expiresAt = expiresAt;
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
   * Copies the fields a reassignment or a merge owns onto this managed instance, so the adapter's
   * save updates the row Hibernate already loaded instead of building a detached one with a stale
   * {@code @Version} (ADR-009) — a fresh, unloaded instance would always carry {@code version = 0}
   * and be mistaken for a new row. {@code sessionToken} is immutable once set (cart.md §2).
   *
   * @param customerId the owning customer, or {@code null} for a guest cart
   * @param expiresAt the new expiry
   */
  public void applyChanges(UUID customerId, Instant expiresAt) {
    this.customerId = customerId;
    this.expiresAt = expiresAt;
  }

  /**
   * Renews {@code expiresAt} on this managed instance, for the same reason {@link #applyChanges}
   * mutates in place: going through the managed entity, rather than a bare {@code UPDATE}, is what
   * makes this write participate in the {@code @Version} check (ADR-009 amendment).
   *
   * @param expiresAt the new expiry
   */
  public void renewExpiry(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the owning customer, or {@code null}
   */
  public UUID getCustomerId() {
    return customerId;
  }

  /**
   * @return the session token
   */
  public String getSessionToken() {
    return sessionToken;
  }

  /**
   * @return when this cart expires
   */
  public Instant getExpiresAt() {
    return expiresAt;
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
