package com.floristeriarosy.infrastructure.persistence.jpa.auth.repository;

import com.floristeriarosy.infrastructure.persistence.entity.auth.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data JPA repository for {@link RefreshTokenEntity} (ADR-002). */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  /**
   * @param tokenHash the SHA-256 hash of the presented plaintext token
   * @return the matching row, if any
   */
  Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);

  /**
   * A custom {@code @Query} in Spring Data is not transactional on its own; {@code @Modifying}
   * needs an explicit {@code @Transactional} here (same pitfall as {@code
   * countActiveOwnersForUpdate} in the admin module).
   *
   * <p>{@code AND r.revokedAt IS NULL} is what makes this the same conditional-write pattern
   * ADR-009 uses elsewhere: under READ COMMITTED, two concurrent refreshes of the same token can
   * both read it as not-yet-revoked, but only one {@code UPDATE} can win this predicate — the
   * second affects zero rows and the caller treats that as the reuse ADR-008 requires it to detect
   * (auth.md, section 10), instead of both requests rotating the same token into two live
   * successors.
   *
   * @param id the row to revoke
   * @param revokedAt the revocation instant
   * @return how many rows were updated: 1 if this call won the race, 0 if the token was already
   *     revoked
   */
  @Modifying
  @Transactional
  @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :revokedAt WHERE r.id = :id AND r.revokedAt IS NULL")
  int revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);
}
