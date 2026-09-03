package com.floristeriarosy.application.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.auth.command.RefreshTokenCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.domain.exception.auth.InvalidRefreshTokenException;
import com.floristeriarosy.domain.exception.auth.SessionRevokedException;
import com.floristeriarosy.domain.exception.auth.TokenExpiredException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link RefreshTokenService}: rotation and reuse detection (auth.md, rules 3.5 and 3.6). */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenReadPort refreshTokenReadPort;
  @Mock private RefreshTokenWritePort refreshTokenWritePort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;
  @Mock private AdminReadPort adminReadPort;
  @Mock private AccessTokenPort accessTokenPort;

  private RefreshTokenService service;

  private Admin admin() {
    return Admin.create(
        AdminId.newId(),
        "encrypted".getBytes(StandardCharsets.UTF_8),
        "hash".getBytes(StandardCharsets.UTF_8),
        "argon2-hash",
        AdminRole.ADMIN);
  }

  private void newService() {
    service =
        new RefreshTokenService(
            refreshTokenReadPort, refreshTokenWritePort, revokeTokenFamilyPort, adminReadPort, accessTokenPort, Duration.ofMinutes(5));
  }

  @Test
  void rejectsAnUnknownToken() {
    newService();
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new RefreshTokenCommand("unknown")))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(refreshTokenWritePort, never()).save(any());
  }

  @Test
  void rejectsAnExpiredFamily() {
    newService();
    Admin admin = admin();
    RefreshToken expired =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash("presented"),
            admin.id().value(),
            SubjectType.ADMIN,
            Instant.now().minusSeconds(1));
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.execute(new RefreshTokenCommand("presented")))
        .isInstanceOf(TokenExpiredException.class);
    verify(revokeTokenFamilyPort, never()).revokeFamily(any());
  }

  @Test
  void revokesTheWholeFamilyWhenAnAlreadyRevokedTokenIsPresentedAgain() {
    newService();
    Admin admin = admin();
    RefreshToken alreadyRevoked =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash("presented"),
            admin.id().value(),
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    alreadyRevoked.revoke(Instant.now());
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.of(alreadyRevoked));

    assertThatThrownBy(() -> service.execute(new RefreshTokenCommand("presented")))
        .isInstanceOf(SessionRevokedException.class);

    verify(revokeTokenFamilyPort).revokeFamily(alreadyRevoked.familyId());
    verify(refreshTokenWritePort, never()).save(any());
  }

  @Test
  void rotatesAValidTokenCopyingTheFamilyExpiry() {
    newService();
    Admin admin = admin();
    Instant familyExpiresAt = Instant.now().plusSeconds(3600);
    RefreshToken presented =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash("presented"),
            admin.id().value(),
            SubjectType.ADMIN,
            familyExpiresAt);
    when(refreshTokenReadPort.findByHash(any())).thenReturn(Optional.of(presented));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(accessTokenPort.issue(any(), any())).thenReturn("new-access-token");

    AuthDto dto = service.execute(new RefreshTokenCommand("presented"));

    assertThat(dto.accessToken()).isEqualTo("new-access-token");
    assertThat(dto.refreshTokenExpiresAt()).isEqualTo(familyExpiresAt);
    assertThat(dto.refreshToken()).isNotEqualTo("presented");
    verify(refreshTokenWritePort).revoke(eq(presented.id()), any());
    verify(refreshTokenWritePort).save(any(RefreshToken.class));
  }
}
