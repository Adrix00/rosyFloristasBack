package com.floristeriarosy.application.auth.port.out;

import com.floristeriarosy.domain.model.auth.RefreshToken;
import java.util.Optional;

/** Reads {@code refresh_tokens} (auth.md, section 8). */
public interface RefreshTokenReadPort {

  /**
   * @param tokenHash SHA-256 of the plaintext token presented in the cookie
   * @return the matching row, if any
   */
  Optional<RefreshToken> findByHash(byte[] tokenHash);
}
