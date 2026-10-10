package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.CheckoutIdempotencyJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.order.repository.IdempotencyKeyJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.owasp.encoder.Encode;
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
  private final CheckoutIdempotencyJdbcRepository jdbcRepository;

  /**
   * @param jpaRepository reads and writes {@code idempotency_keys}
   * @param jdbcRepository selects and claims the expired {@code PENDING} rows
   */
  public CheckoutIdempotencyPersistenceAdapter(
      IdempotencyKeyJpaRepository jpaRepository, CheckoutIdempotencyJdbcRepository jdbcRepository) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, if any
   */
  @Override
  public Optional<IdempotencyLock> find(String idempotencyKey) {
    LOGGER.debug("find idempotencyKey={}", Encode.forJava(idempotencyKey));
    return jpaRepository.findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT).map(this::toLock);
  }

  /**
   * @param idempotencyKey the client-supplied key
   * @return the current lock, row-locked until the caller's transaction ends, if any
   */
  @Override
  public Optional<IdempotencyLock> findForUpdate(String idempotencyKey) {
    LOGGER.debug("findForUpdate idempotencyKey={}", Encode.forJava(idempotencyKey));
    return jpaRepository
        .findForUpdateByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
        .map(this::toLock);
  }

  /**
   * @param now the instant "expired" is measured against
   * @param limit the most keys to return
   * @return the keys of expired {@code PENDING} checkouts, oldest first
   */
  @Override
  public List<String> findExpiredPendingKeys(Instant now, int limit) {
    return jdbcRepository.findExpiredPendingKeys(ENDPOINT, now, limit);
  }

  /**
   * @param idempotencyKey a candidate key
   * @param now the instant "expired" is measured against
   * @return the claimed row, or empty if it is no longer claimable
   */
  @Override
  public Optional<IdempotencyLock> lockExpiredPending(String idempotencyKey, Instant now) {
    return jdbcRepository.lockExpiredPending(idempotencyKey, ENDPOINT, now);
  }

  /**
   * @param entity the persisted row
   * @return its lock view
   */
  private IdempotencyLock toLock(IdempotencyKeyEntity entity) {
    return new IdempotencyLock(
        entity.getStatus(),
        entity.getRequestFingerprint(),
        entity.getResourceId(),
        entity.getCheckoutPayloadEncrypted());
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
    LOGGER.debug("insertPending idempotencyKey={} resourceId={}", Encode.forJava(idempotencyKey), resourceId);
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
    LOGGER.debug("insertPending idempotencyKey={} -> inserted", Encode.forJava(idempotencyKey));
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
    LOGGER.debug("resetToPending idempotencyKey={} resourceId={}", Encode.forJava(idempotencyKey), resourceId);
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
    LOGGER.debug("resetToPending idempotencyKey={} -> reset", Encode.forJava(idempotencyKey));
  }

  /**
   * @param idempotencyKey the key to resolve
   * @param responseStatus the HTTP status the original request resolved to
   */
  @Override
  public void markCompleted(String idempotencyKey, int responseStatus) {
    LOGGER.debug("markCompleted idempotencyKey={} responseStatus={}", Encode.forJava(idempotencyKey), responseStatus);
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to complete for " + idempotencyKey));
    entity.setStatus("COMPLETED");
    entity.setResponseStatus((short) responseStatus);
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markCompleted idempotencyKey={} -> completed", Encode.forJava(idempotencyKey));
  }

  /**
   * @param idempotencyKey the key to resolve
   */
  @Override
  public void markFailed(String idempotencyKey) {
    LOGGER.debug("markFailed idempotencyKey={}", Encode.forJava(idempotencyKey));
    IdempotencyKeyEntity entity =
        jpaRepository
            .findByIdempotencyKeyAndEndpoint(idempotencyKey, ENDPOINT)
            .orElseThrow(() -> new IllegalStateException("No row to fail for " + idempotencyKey));
    entity.setStatus("FAILED");
    entity.setCheckoutPayloadEncrypted(null);
    jpaRepository.saveAndFlush(entity);
    LOGGER.debug("markFailed idempotencyKey={} -> failed", Encode.forJava(idempotencyKey));
  }
}
