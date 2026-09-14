package com.floristeriarosy.application.order.dto;

import java.util.UUID;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED} (ADR-011)
 * @param requestFingerprint the stored fingerprint, to compare against a retry's
 * @param resourceId the order id this key resolves to, once known
 */
public record IdempotencyLock(String status, byte[] requestFingerprint, UUID resourceId) {

  /** Defensively copies {@code requestFingerprint} (SpotBugs EI_EXPOSE_REP2). */
  public IdempotencyLock {
    requestFingerprint = requestFingerprint == null ? null : requestFingerprint.clone();
  }

  /** @return a defensive copy of {@code requestFingerprint} (SpotBugs EI_EXPOSE_REP) */
  @Override
  public byte[] requestFingerprint() {
    return requestFingerprint == null ? null : requestFingerprint.clone();
  }
}
