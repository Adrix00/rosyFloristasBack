package com.floristeriarosy.infrastructure.persistence.jdbc.order.repository;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Filtered reads over {@code idempotency_keys} for the abandoned-checkout cleanup (ADR-002: a
 * filtered query is JDBC, a plain lookup is JPA).
 */
@Repository
public class CheckoutIdempotencyJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutIdempotencyJdbcRepository.class);

  private static final String EXPIRED_PENDING_KEYS_SQL =
      "SELECT idempotency_key FROM idempotency_keys"
          + " WHERE endpoint = ? AND status = 'PENDING' AND expires_at < ?"
          + " ORDER BY expires_at LIMIT ?";

  private static final String LOCK_EXPIRED_PENDING_SQL =
      "SELECT status, request_fingerprint, resource_id, checkout_payload_encrypted"
          + " FROM idempotency_keys"
          + " WHERE idempotency_key = ? AND endpoint = ? AND status = 'PENDING' AND expires_at < ?"
          + " FOR UPDATE SKIP LOCKED";

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the queries
   */
  public CheckoutIdempotencyJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param endpoint the endpoint the rows belong to
   * @param now the instant "expired" is measured against
   * @param limit the most keys to return
   * @return the keys of {@code PENDING} rows past their {@code expires_at}, oldest first; not locked
   */
  public List<String> findExpiredPendingKeys(String endpoint, Instant now, int limit) {
    LOGGER.debug("findExpiredPendingKeys endpoint={} limit={}", endpoint, limit);
    List<String> keys =
        jdbcTemplate.queryForList(
            EXPIRED_PENDING_KEYS_SQL, String.class, endpoint, Timestamp.from(now), limit);
    LOGGER.debug("findExpiredPendingKeys -> {} keys", keys.size());
    return keys;
  }

  /**
   * Re-checks the row is still {@code PENDING} and expired and locks it, skipping it when another
   * transaction (a Redsys notification confirming it) already holds the lock.
   *
   * @param idempotencyKey the key to claim
   * @param endpoint the endpoint the row belongs to
   * @param now the instant "expired" is measured against
   * @return the locked row, or empty if it is no longer pending, not yet expired, or locked elsewhere
   */
  public Optional<IdempotencyLock> lockExpiredPending(
      String idempotencyKey, String endpoint, Instant now) {
    LOGGER.debug("lockExpiredPending idempotencyKey={}", Encode.forJava(idempotencyKey));
    Optional<IdempotencyLock> lock =
        jdbcTemplate
            .query(
                LOCK_EXPIRED_PENDING_SQL,
                (rs, rowNum) ->
                    new IdempotencyLock(
                        rs.getString("status"),
                        rs.getBytes("request_fingerprint"),
                        rs.getObject("resource_id", UUID.class),
                        rs.getBytes("checkout_payload_encrypted")),
                idempotencyKey,
                endpoint,
                Timestamp.from(now))
            .stream()
            .findFirst();
    LOGGER.debug(
        "lockExpiredPending idempotencyKey={} -> claimed={}",
        Encode.forJava(idempotencyKey),
        lock.isPresent());
    return lock;
  }
}
