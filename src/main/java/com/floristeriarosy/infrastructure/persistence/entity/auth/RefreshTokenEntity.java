package com.floristeriarosy.infrastructure.persistence.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code refresh_tokens} table. No {@code @Version}: rows are never updated
 * concurrently by two callers of the same use case (ADR-008's single-write-path invariant already
 * rules that out).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

  @Id private UUID id;

  @Column(name = "token_hash", nullable = false)
  private byte[] tokenHash;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "admin_user_id")
  private UUID adminUserId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected RefreshTokenEntity() {}

  /**
   * @param id the primary key
   * @param tokenHash the SHA-256 hash of the plaintext token
   * @param customerId the owning customer, exclusive with {@code adminUserId}
   * @param adminUserId the owning admin, exclusive with {@code customerId}
   * @param familyId the rotation family this row belongs to
   * @param expiresAt the family's absolute expiry
   * @param revokedAt when this row was revoked, or {@code null} if still usable
   * @param createdAt when this row was created, or {@code null} for a not-yet-persisted row
   */
  public RefreshTokenEntity(
      UUID id,
      byte[] tokenHash,
      UUID customerId,
      UUID adminUserId,
      UUID familyId,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {
    this.id = id;
    this.tokenHash = tokenHash.clone();
    this.customerId = customerId;
    this.adminUserId = adminUserId;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.createdAt = createdAt;
  }

  /**
   * {@code created_at} has a DB-side {@code DEFAULT now()}; set here so a fresh save reflects it.
   */
  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the SHA-256 hash of the plaintext token
   */
  public byte[] getTokenHash() {
    return tokenHash.clone();
  }

  /**
   * @return the owning customer, or {@code null} if this row belongs to an admin
   */
  public UUID getCustomerId() {
    return customerId;
  }

  /**
   * @return the owning admin, or {@code null} if this row belongs to a customer
   */
  public UUID getAdminUserId() {
    return adminUserId;
  }

  /**
   * @return the rotation family this row belongs to
   */
  public UUID getFamilyId() {
    return familyId;
  }

  /**
   * @return the family's absolute expiry
   */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * @return when this row was revoked, or {@code null} if still usable
   */
  public Instant getRevokedAt() {
    return revokedAt;
  }

  /**
   * @return when this row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
