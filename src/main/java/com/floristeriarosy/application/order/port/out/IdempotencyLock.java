package com.floristeriarosy.application.order.port.out;

import java.util.UUID;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED} (ADR-011)
 * @param requestFingerprint the stored fingerprint, to compare against a retry's
 * @param resourceId the order id this key resolves to, once known
 */
public record IdempotencyLock(String status, byte[] requestFingerprint, UUID resourceId) {}
