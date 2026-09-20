package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.ReleaseExpiredCheckoutsUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.support.CheckoutReservationReleaser;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implements {@link ReleaseExpiredCheckoutsUseCase}. Not {@code @Transactional}: each row is
 * resolved through a {@link TransactionTemplate}, because a self-invoked {@code @Transactional}
 * method bypasses its proxy and would silently run every row in one shared transaction. Per row it
 * claims the row under {@code FOR UPDATE SKIP LOCKED} (so a Redsys notification confirming it wins
 * and the cleanup simply skips it), releases the Fase-1 reservation and marks the row {@code
 * FAILED}. A row whose payload cannot be read is left {@code PENDING} and retried next run.
 */
@Service
public class ReleaseExpiredCheckoutsService implements ReleaseExpiredCheckoutsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseExpiredCheckoutsService.class);
  private static final int BATCH_SIZE = 100;

  private final CheckoutIdempotencyPort idempotencyPort;
  private final PendingCheckoutPayloadReader payloadReader;
  private final CheckoutReservationReleaser reservationReleaser;
  private final TransactionTemplate transactionTemplate;

  /**
   * @param idempotencyPort lists and claims the expired {@code PENDING} rows and marks them {@code
   *     FAILED}
   * @param payloadReader decrypts and deserializes the pending payload (ADR-005)
   * @param reservationReleaser gives back the reserved stock and discount units
   * @param transactionManager opens one transaction per row
   */
  public ReleaseExpiredCheckoutsService(
      CheckoutIdempotencyPort idempotencyPort,
      PendingCheckoutPayloadReader payloadReader,
      CheckoutReservationReleaser reservationReleaser,
      PlatformTransactionManager transactionManager) {
    this.idempotencyPort = idempotencyPort;
    this.payloadReader = payloadReader;
    this.reservationReleaser = reservationReleaser;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * @return how many checkouts were released in this run
   */
  @Override
  public int execute() {
    Instant now = Instant.now();
    List<String> candidates = idempotencyPort.findExpiredPendingKeys(now, BATCH_SIZE);
    LOGGER.debug("releaseExpiredCheckouts candidates={}", candidates.size());
    int released = 0;
    for (String key : candidates) {
      if (releaseOne(key, now)) {
        released++;
      }
    }
    LOGGER.debug("releaseExpiredCheckouts -> released={}", released);
    return released;
  }

  /**
   * @param key a candidate checkout
   * @param now the instant "expired" is measured against
   * @return whether this call released it; {@code false} when it was resolved or claimed elsewhere
   *     meanwhile, or when its own transaction failed (logged, left {@code PENDING} for the next
   *     run)
   */
  private boolean releaseOne(String key, Instant now) {
    try {
      return Boolean.TRUE.equals(transactionTemplate.execute(status -> claimAndRelease(key, now)));
    } catch (RuntimeException e) {
      LOGGER.error("releaseExpiredCheckouts idempotencyKey={} -> failed", Encode.forJava(key), e);
      return false;
    }
  }

  /**
   * @param key a candidate checkout
   * @param now the instant "expired" is measured against
   * @return whether the row was claimed and resolved
   */
  private boolean claimAndRelease(String key, Instant now) {
    Optional<IdempotencyLock> lock = idempotencyPort.lockExpiredPending(key, now);
    if (lock.isEmpty()) {
      LOGGER.debug(
          "releaseExpiredCheckouts idempotencyKey={} -> no longer claimable", Encode.forJava(key));
      return false;
    }
    Optional<PendingCheckoutPayload> payload = readPayload(lock.get(), key);
    if (payload.isEmpty()) {
      return false;
    }
    reservationReleaser.release(payload.get());
    idempotencyPort.markFailed(key);
    return true;
  }

  /**
   * @param lock the claimed row
   * @param key its idempotency key, for the log only
   * @return the decrypted payload, or empty when it cannot be read. The row is then left {@code
   *     PENDING}: an unreadable payload is most likely a wrong or changed encryption key at deploy,
   *     and marking it {@code FAILED} without releasing would leak the reservation for good. Fixing
   *     the key lets the next run recover it, at the cost of one ERROR per run for each such row.
   */
  private Optional<PendingCheckoutPayload> readPayload(IdempotencyLock lock, String key) {
    try {
      return Optional.of(payloadReader.read(lock.checkoutPayloadEncrypted()));
    } catch (RuntimeException e) {
      LOGGER.error(
          "releaseExpiredCheckouts idempotencyKey={} -> payload unreadable ({}), leaving it PENDING",
          Encode.forJava(key),
          e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
