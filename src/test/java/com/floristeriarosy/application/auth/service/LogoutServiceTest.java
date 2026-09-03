package com.floristeriarosy.application.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.auth.command.LogoutCommand;
import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link LogoutService}: revokes the presenting device's family (auth.md, rule 3.7). */
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

  @Mock private RefreshTokenReadPort refreshTokenReadPort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;

  private LogoutService service;

  @Test
  void isANoOpWhenTheCookieIsAbsent() {
    service = new LogoutService(refreshTokenReadPort, revokeTokenFamilyPort);

    service.execute(new LogoutCommand(null));

    verify(refreshTokenReadPort, never()).findByHash(any());
    verify(revokeTokenFamilyPort, never()).revokeFamily(any());
  }

  @Test
  void revokesOnlyTheFamilyOfThePresentedCookie() {
    service = new LogoutService(refreshTokenReadPort, revokeTokenFamilyPort);
    RefreshToken token =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash("cookie-value"),
            UUID.randomUUID(),
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    when(refreshTokenReadPort.findByHash(RefreshToken.hash("cookie-value")))
        .thenReturn(Optional.of(token));

    service.execute(new LogoutCommand("cookie-value"));

    verify(revokeTokenFamilyPort).revokeFamily(token.familyId());
  }

  @Test
  void isIdempotentWhenTheCookieMatchesNoRow() {
    service = new LogoutService(refreshTokenReadPort, revokeTokenFamilyPort);
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.empty());

    service.execute(new LogoutCommand("already-gone"));

    verify(revokeTokenFamilyPort, never()).revokeFamily(any());
  }
}
