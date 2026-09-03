package com.floristeriarosy.application.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.auth.command.LogoutAllCommand;
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

/** {@link LogoutAllService}: revokes every family of the subject (auth.md, rule 3.7). */
@ExtendWith(MockitoExtension.class)
class LogoutAllServiceTest {

  @Mock private RefreshTokenReadPort refreshTokenReadPort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;

  private LogoutAllService service;

  @Test
  void isANoOpWhenTheCookieIsAbsent() {
    service = new LogoutAllService(refreshTokenReadPort, revokeTokenFamilyPort);

    service.execute(new LogoutAllCommand(null));

    verify(refreshTokenReadPort, never()).findByHash(any());
    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }

  @Test
  void revokesEveryFamilyOfTheSubjectTheCookieBelongsTo() {
    service = new LogoutAllService(refreshTokenReadPort, revokeTokenFamilyPort);
    UUID subjectId = UUID.randomUUID();
    RefreshToken token =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash("cookie-value"),
            subjectId,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    when(refreshTokenReadPort.findByHash(RefreshToken.hash("cookie-value")))
        .thenReturn(Optional.of(token));

    service.execute(new LogoutAllCommand("cookie-value"));

    verify(revokeTokenFamilyPort).revokeAllForSubject(subjectId);
  }

  @Test
  void isIdempotentWhenTheCookieMatchesNoRow() {
    service = new LogoutAllService(refreshTokenReadPort, revokeTokenFamilyPort);
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.empty());

    service.execute(new LogoutAllCommand("already-gone"));

    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }
}
