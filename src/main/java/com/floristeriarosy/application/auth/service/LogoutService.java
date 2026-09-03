package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.auth.command.LogoutCommand;
import com.floristeriarosy.application.auth.port.in.LogoutUseCase;
import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link LogoutUseCase}: revokes the family of the device that presented the cookie
 * (auth.md, rule 3.7). Idempotent — a missing or already-revoked cookie is not an error.
 */
@Service
@Transactional
public class LogoutService implements LogoutUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(LogoutService.class);

  private final RefreshTokenReadPort refreshTokenReadPort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;

  /**
   * @param refreshTokenReadPort resolves the cookie to its rotation family
   * @param revokeTokenFamilyPort revokes that family
   */
  public LogoutService(
      RefreshTokenReadPort refreshTokenReadPort, RevokeTokenFamilyPort revokeTokenFamilyPort) {
    this.refreshTokenReadPort = refreshTokenReadPort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
  }

  /**
   * @param command the plaintext refresh token from the cookie, or {@code null} if absent
   */
  @Override
  public void execute(LogoutCommand command) {
    LOGGER.debug("logout");
    if (command.refreshToken() == null) {
      LOGGER.debug("logout -> no cookie, nothing to do");
      return;
    }

    Optional<RefreshToken> presented =
        refreshTokenReadPort.findByHash(RefreshToken.hash(command.refreshToken()));
    presented.ifPresent(
        refreshToken -> revokeTokenFamilyPort.revokeFamily(refreshToken.familyId()));
    LOGGER.debug("logout -> done");
  }
}
