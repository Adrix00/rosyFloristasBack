package com.floristeriarosy.application.order.dto;

import java.util.UUID;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED} (ADR-011)
 * @param requestFingerprint the stored fingerprint, to compare against a retry's
 * @param resourceId the order id this key resolves to, once known
 * @param checkoutPayloadEncrypted the encrypted {@link PendingCheckoutPayload} kept while {@code
 *     PENDING}; {@code null} once resolved
 */
public record IdempotencyLock(
    String status, byte[] requestFingerprint, UUID resourceId, byte[] checkoutPayloadEncrypted) {

  /**
   * Defensively copies {@code requestFingerprint} and {@code checkoutPayloadEncrypted} (SpotBugs
   * EI_EXPOSE_REP2).
   */
  public IdempotencyLock {
    requestFingerprint = requestFingerprint == null ? null : requestFingerprint.clone();
    checkoutPayloadEncrypted =
        checkoutPayloadEncrypted == null ? null : checkoutPayloadEncrypted.clone();
  }

  /** @return a defensive copy of {@code requestFingerprint} (SpotBugs EI_EXPOSE_REP) */
  @Override
  public byte[] requestFingerprint() {
    return requestFingerprint == null ? null : requestFingerprint.clone();
  }

  /** @return a defensive copy of the encrypted payload, or {@code null} once resolved */
  @Override
  public byte[] checkoutPayloadEncrypted() {
    return checkoutPayloadEncrypted == null ? null : checkoutPayloadEncrypted.clone();
  }
}
