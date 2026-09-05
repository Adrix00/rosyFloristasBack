package com.floristeriarosy.infrastructure.persistence.entity.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code carts} table. No {@code version} column, deliberately: this aggregate
 * is excluded from ADR-009's optimistic-locking list (cart.md, section 2) — a cart is never edited
 * by two administrators at once, only ever by its own owner.
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
