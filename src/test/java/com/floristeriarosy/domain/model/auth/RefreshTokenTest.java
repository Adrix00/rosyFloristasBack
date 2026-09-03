package com.floristeriarosy.domain.model.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final byte[] TOKEN_HASH = "hash".getBytes(StandardCharsets.UTF_8);
  private static final UUID SUBJECT_ID = UUID.randomUUID();

  @Test
  void rotateCopiesTheFamilyExpiryInsteadOfExtendingIt() {
    Instant familyExpiresAt = Instant.now().plusSeconds(3600);
    RefreshToken original =
        RefreshToken.startFamily(
            RefreshTokenId.newId(), TOKEN_HASH, SUBJECT_ID, SubjectType.ADMIN, familyExpiresAt);

    RefreshToken rotated =
        original.rotate(RefreshTokenId.newId(), "new-hash".getBytes(StandardCharsets.UTF_8));

    assertThat(rotated.expiresAt()).isEqualTo(familyExpiresAt);
    assertThat(rotated.familyId()).isEqualTo(original.familyId());
  }

  @Test
  void rotateProducesANewIdAndClearsRevocationAndCreation() {
    RefreshToken original =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    RefreshTokenId newId = RefreshTokenId.newId();

    RefreshToken rotated = original.rotate(newId, "new-hash".getBytes(StandardCharsets.UTF_8));

    assertThat(rotated.id()).isEqualTo(newId);
    assertThat(rotated.id()).isNotEqualTo(original.id());
    assertThat(rotated.isRevoked()).isFalse();
    assertThat(rotated.createdAt()).isNull();
  }

  @Test
  void startFamilyGivesEachLoginItsOwnFamilyId() {
    RefreshToken first =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    RefreshToken second =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));

    assertThat(first.familyId()).isNotEqualTo(second.familyId());
  }

  @Test
  void isExpiredIsTrueOnceNowIsAfterTheFamilyExpiry() {
    RefreshToken token =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().minusSeconds(1));

    assertThat(token.isExpired(Instant.now())).isTrue();
  }

  @Test
  void isExpiredIsFalseBeforeTheFamilyExpiry() {
    RefreshToken token =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));

    assertThat(token.isExpired(Instant.now())).isFalse();
  }

  @Test
  void revokeMarksTheRowRevoked() {
    RefreshToken token =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            TOKEN_HASH,
            SUBJECT_ID,
            SubjectType.ADMIN,
            Instant.now().plusSeconds(3600));
    assertThat(token.isRevoked()).isFalse();

    token.revoke(Instant.now());

    assertThat(token.isRevoked()).isTrue();
  }

  @Test
  void generatePlaintextNeverRepeats() {
    String first = RefreshToken.generatePlaintext();
    String second = RefreshToken.generatePlaintext();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void hashIsDeterministicAndProducesA32ByteSha256Digest() {
    byte[] first = RefreshToken.hash("same-token");
    byte[] second = RefreshToken.hash("same-token");
    byte[] different = RefreshToken.hash("different-token");

    assertThat(first).isEqualTo(second);
    assertThat(first).hasSize(32);
    assertThat(first).isNotEqualTo(different);
  }
}
