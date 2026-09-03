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
   * @param id the row to revoke
   * @param revokedAt the revocation instant
   */
  void revoke(RefreshTokenId id, Instant revokedAt);
}
