package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code POST /checkout} idempotency lock (ADR-011, ADR-017): holds the Fase-1 reservation's
 * outcome — and, while {@code PENDING}, the encrypted payload Fase 3a/3b needs — for the whole
 * window a customer spends paying at Redsys.
 */
public interface CheckoutIdempotencyPort {

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, if a row exists for this key on {@code POST /checkout}
   */
  Optional<IdempotencyLock> find(String idempotencyKey);

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the SHA-256 of body+cart (ADR-011, payment.md §3.8)
   * @param resourceId the order id minted for this attempt
   * @param checkoutPayloadEncrypted the encrypted {@link
   *     com.floristeriarosy.application.order.dto.PendingCheckoutPayload}
   * @param expiresAt when this reservation is abandoned if unconfirmed (ADR-017: 10 minutes)
   * @throws com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException a
   *     concurrent request already holds this key
   */
  void insertPending(
      String idempotencyKey,
      byte[] requestFingerprint,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant expiresAt);

  /**
   * Re-arms a {@code FAILED} row for a fresh attempt with the same key and fingerprint (order.md,
   * §10 edge case: retry after a payment decline restarts from Fase 1).
   *
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the (unchanged) fingerprint
   * @param resourceId the newly minted order id for this attempt
   * @param checkoutPayloadEncrypted the newly encrypted payload
   * @param expiresAt the new expiry
   */
  void resetToPending(
      String idempotencyKey,
      byte[] requestFingerprint,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant expiresAt);

  /**
   * @param idempotencyKey the key to resolve
   * @param responseStatus the HTTP status the original request resolved to
   */
  void markCompleted(String idempotencyKey, int responseStatus);

  /**
   * @param idempotencyKey the key to resolve
   */
  void markFailed(String idempotencyKey);
}
