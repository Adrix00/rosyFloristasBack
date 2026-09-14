package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.order.repository.IdempotencyKeyJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link CheckoutIdempotencyPort} over JPA, scoped to {@code POST /checkout} (ADR-011,
 * ADR-017).
 */
@Repository
public class CheckoutIdempotencyPersistenceAdapter implements CheckoutIdempotencyPort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CheckoutIdempotencyPersistenceAdapter.class);
  private static final String ENDPOINT = "POST /checkout";

  private final IdempotencyKeyJpaRepository jpaRepository;

  /**
   * @param jpaRepository reads and writes {@code idempotency_keys}
   */
  public CheckoutIdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, if any
   */
  @Override
  public Optional<IdempotencyLock> find(String idempotencyKey) {
    LOGGER.debug("find idempotencyKey={}", idempotencyKey);
    return jpaRepository
        .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
        .map(
            entity ->
                new IdempotencyLock(
                    entity.getStatus(), entity.getRequestFingerprint(), entity.getResourceId()));
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the SHA-256 of body+cart
   * @param resourceId the order id minted for this attempt
   * @param checkoutPayloadEncrypted the encrypted pending-checkout payload
   * @param expiresAt when this reservation is abandoned if unconfirmed
   * @throws IdempotencyOperationInProgressException a concurrent request already holds this key
   */
  @Override
  public void insertPending(
      String idempotencyKey,
      byte[] requestFingerprint,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant expiresAt) {
    LOGGER.debug("insertPending idempotencyKey={} resourceId={}", idempotencyKey, resourceId);
    IdempotencyKeyEntity entity =
        new IdempotencyKeyEntity(
            UUID.randomUUID(),
            idempotencyKey,
            ENDPOINT,
            requestFingerprint,
            "PENDING",
            null,
            resourceId,
            checkoutPayloadEncrypted,
            Instant.now(),
            expiresAt);
    try {
      jpaRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException violation) {
      throw new IdempotencyOperationInProgressException(
          "Idempotency-Key " + idempotencyKey + " is already in use");
    }
    LOGGER.debug("insertPending idempotencyKey={} -> inserted", idempotencyKey);
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @param requestFingerprint the (unchanged) fingerprint
   * @param resourceId the newly minted order id for this attempt
   * @param checkoutPayloadEncrypted the newly encrypted payload
   * @param expiresAt the new expiry
   */
  @Override
  public void resetToPending(
      String idempotencyKey,
      byte[] requestFingerprint,
      UUID resourceId,
      byte[] checkoutPayloadEncrypted,
      Instant expiresAt) {
    LOGGER.debug("resetToPending idempotencyKey={} resourceId={}", idempotencyKey, resourceId);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to reset for " + idempotencyKey));
    entity.setStatus("PENDING");
    entity.setRequestFingerprint(requestFingerprint);
    entity.setResourceId(resourceId);
    entity.setCheckoutPayloadEncrypted(checkoutPayloadEncrypted);
    entity.setResponseStatus(null);
    entity.setExpiresAt(expiresAt);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("resetToPending idempotencyKey={} -> reset", idempotencyKey);
  }

  /**
   * @param idempotencyKey the key to resolve
   * @param responseStatus the HTTP status the original request resolved to
   */
  @Override
  public void markCompleted(String idempotencyKey, int responseStatus) {
    LOGGER.debug("markCompleted idempotencyKey={} responseStatus={}", idempotencyKey, responseStatus);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to complete for " + idempotencyKey));
    entity.setStatus("COMPLETED");
    entity.setResponseStatus((short) responseStatus);
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markCompleted idempotencyKey={} -> completed", idempotencyKey);
  }

  /**
   * @param idempotencyKey the key to resolve
   */
  @Override
  public void markFailed(String idempotencyKey) {
    LOGGER.debug("markFailed idempotencyKey={}", idempotencyKey);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to fail for " + idempotencyKey));
    entity.setStatus("FAILED");
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markFailed idempotencyKey={} -> failed", idempotencyKey);
  }
}
