package com.floristeriarosy.application.auth.port.out;

import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.time.Instant;

/** Writes {@code refresh_tokens} (auth.md, section 8). */
public interface RefreshTokenWritePort {

  /**
   * @param refreshToken the row to insert
   * @return the saved row, with timestamps populated by the database
   */
  RefreshToken save(RefreshToken refreshToken);

  /**
   * Conditional on the row not already being revoked (ADR-009's conditional-write pattern): two
   * concurrent rotations of the same token can both read it as live, but only one revoke wins.
   *
   * @param id the row to revoke
   * @param revokedAt the revocation instant
   * @return whether this call is the one that revoked it — {@code false} means it was already
   *     revoked, the signal {@code RefreshTokenService} treats as reuse
   */
  boolean revoke(RefreshTokenId id, Instant revokedAt);
}
