package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.RefreshTokenCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.domain.exception.auth.InvalidRefreshTokenException;
import com.floristeriarosy.domain.exception.auth.SessionRevokedException;
import com.floristeriarosy.domain.exception.auth.TokenExpiredException;

/** Renews a session, single-use rotation (auth.md, rule 3.5; ADR-008). */
public interface RefreshTokenUseCase {

  /**
   * @param command the plaintext refresh token from the cookie
   * @return the newly rotated access and refresh tokens
   * @throws InvalidRefreshTokenException no row matches the presented token
   * @throws TokenExpiredException the family's absolute expiry has passed
   * @throws SessionRevokedException the token was already revoked (reuse detected); the whole
   *     family is revoked as a side effect
   */
  AuthDto execute(RefreshTokenCommand command);
}
