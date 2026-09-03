package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.auth.command.LogoutAllCommand;
import com.floristeriarosy.application.auth.port.in.LogoutAllUseCase;
import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link LogoutAllUseCase}: revokes every family of the subject that presented the
 * cookie, this device included (auth.md, rule 3.7). Idempotent — a missing or already-revoked
 * cookie is not an error.
 */
@Service
@Transactional
public class LogoutAllService implements LogoutAllUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(LogoutAllService.class);

  private final RefreshTokenReadPort refreshTokenReadPort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;

  /**
   * @param refreshTokenReadPort resolves the cookie to its subject
   * @param revokeTokenFamilyPort revokes every family of that subject
   */
  public LogoutAllService(
      RefreshTokenReadPort refreshTokenReadPort, RevokeTokenFamilyPort revokeTokenFamilyPort) {
    this.refreshTokenReadPort = refreshTokenReadPort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
  }

  /**
   * @param command the plaintext refresh token from the cookie, or {@code null} if absent
   */
  @Override
  public void execute(LogoutAllCommand command) {
    LOGGER.debug("logoutAll");
    if (command.refreshToken() == null) {
      LOGGER.debug("logoutAll -> no cookie, nothing to do");
      return;
    }

    Optional<RefreshToken> presented =
        refreshTokenReadPort.findByHash(RefreshToken.hash(command.refreshToken()));
    presented.ifPresent(
        refreshToken -> revokeTokenFamilyPort.revokeAllForSubject(refreshToken.subjectId()));
    LOGGER.debug("logoutAll -> done");
  }
}
