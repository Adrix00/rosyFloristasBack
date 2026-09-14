package com.floristeriarosy.infrastructure.persistence.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping of {@code idempotency_keys} (ADR-011), the columns Phase 3a/3b use. */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

  @Id private UUID id;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(nullable = false, length = 120)
  private String endpoint;

  @Column(name = "request_fingerprint", nullable = false)
  private byte[] requestFingerprint;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "response_status")
  private Short responseStatus;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "checkout_payload_encrypted")
  private byte[] checkoutPayloadEncrypted;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Required by JPA; not for application use. */
  protected IdempotencyKeyEntity() {}

  /**
   * @param id the primary key
   * @param idempotencyKey the client-supplied key
   * @param endpoint which endpoint this lock guards
   * @param requestFingerprint the SHA-256 of body+cart
   * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
   * @param responseStatus the resolved HTTP status, or {@code null} until resolved
   * @param resourceId the order id this key resolves to
   * @param checkoutPayloadEncrypted the encrypted pending-checkout payload, or {@code null} once
   *     resolved
   * @param createdAt when the row was created
   * @param expiresAt when this reservation is abandoned if unconfirmed
   */
  public IdempotencyKeyEntity(
      UUID id,
      String idempotencyKey,
      String endpoint,
      byte[] requestFingerprint,
      String status,
      Short responseStatus,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.idempotencyKey = idempotencyKey;
    this.endpoint = endpoint;
    this.requestFingerprint = requestFingerprint == null ? null : requestFingerprint.clone();
    this.status = status;
    this.responseStatus = responseStatus;
    this.resourceId = resourceId;
    this.checkoutPayloadEncrypted =
        checkoutPayloadEncrypted == null ? null : checkoutPayloadEncrypted.clone();
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the client-supplied key */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** @return which endpoint this lock guards */
  public String getEndpoint() {
    return endpoint;
  }

  /** @return a defensive copy of the stored request fingerprint */
  public byte[] getRequestFingerprint() {
    return requestFingerprint == null ? null : requestFingerprint.clone();
  }

  /** @param requestFingerprint the new fingerprint (retry after {@code FAILED}) */
  public void setRequestFingerprint(byte[] requestFingerprint) {
    this.requestFingerprint = requestFingerprint == null ? null : requestFingerprint.clone();
  }

  /** @return the current status */
  public String getStatus() {
    return status;
  }

  /** @param status the new status */
  public void setStatus(String status) {
    this.status = status;
  }

  /** @return the resolved HTTP status, or {@code null} */
  public Short getResponseStatus() {
    return responseStatus;
  }

  /** @param responseStatus the resolved HTTP status */
  public void setResponseStatus(Short responseStatus) {
    this.responseStatus = responseStatus;
  }

  /** @return the order id this key resolves to */
  public UUID getResourceId() {
    return resourceId;
  }

  /** @param resourceId the order id this key resolves to */
  public void setResourceId(UUID resourceId) {
    this.resourceId = resourceId;
  }

  /** @return a defensive copy of the encrypted pending-checkout payload, or {@code null} */
  public byte[] getCheckoutPayloadEncrypted() {
    return checkoutPayloadEncrypted == null ? null : checkoutPayloadEncrypted.clone();
  }

  /** @param checkoutPayloadEncrypted the encrypted payload, or {@code null} to clear it */
  public void setCheckoutPayloadEncrypted(byte[] checkoutPayloadEncrypted) {
    this.checkoutPayloadEncrypted =
        checkoutPayloadEncrypted == null ? null : checkoutPayloadEncrypted.clone();
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when this reservation is abandoned if unconfirmed */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** @param expiresAt the new expiry (retry after {@code FAILED}) */
  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
