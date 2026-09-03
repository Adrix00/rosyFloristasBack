package com.floristeriarosy.domain.model.auth;

import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the refresh-token rotation chain (auth.md, section 2; ADR-008). Every row
 * descended from the same login shares {@code familyId} and {@code expiresAt}: rotation only ever
 * copies the family's original expiry onto the child row, never extends it.
 */
public final class RefreshToken {

  private static final Logger LOGGER = LoggerFactory.getLogger(RefreshToken.class);

  private static final int PLAINTEXT_BYTES = 32;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenId id;
  private final byte[] tokenHash;
  private final UUID subjectId;
  private final SubjectType subjectType;
  private final UUID familyId;
  private final Instant expiresAt;
  private Instant revokedAt;
  private final Instant createdAt;

  private RefreshToken(
      RefreshTokenId id,
      byte[] tokenHash,
      UUID subjectId,
      SubjectType subjectType,
      UUID familyId,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {
    this.id = id;
    this.tokenHash = tokenHash;
    this.subjectId = subjectId;
    this.subjectType = subjectType;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.createdAt = createdAt;
  }

  /**
   * Starts a new rotation family, e.g. on a successful login (auth.md, rule 3.4). {@code expiresAt}
   * becomes the absolute cap every descendant row copies (ADR-008).
   *
   * @param id application-generated identifier
   * @param tokenHash SHA-256 of the plaintext token (ADR-005: high-entropy, not a password)
   * @param subjectId the customer or admin this session belongs to
   * @param subjectType {@code CUSTOMER} or {@code ADMIN}
   * @param expiresAt the family's absolute expiry: 30 days for a customer, 12 hours for an admin
   * @return the new, not-yet-persisted refresh token, head of a new family
   */
  public static RefreshToken startFamily(
      RefreshTokenId id,
      byte[] tokenHash,
      UUID subjectId,
      SubjectType subjectType,
      Instant expiresAt) {
    LOGGER.debug("startFamily id={} subjectId={} subjectType={}", id, subjectId, subjectType);
    RefreshToken result =
        new RefreshToken(
            id,
            tokenHash.clone(),
            subjectId,
            subjectType,
            UUID.randomUUID(),
            expiresAt,
            null,
            null);
    LOGGER.debug("startFamily id={} -> familyId={}", id, result.familyId);
    return result;
  }

  /**
   * Rebuilds a refresh token from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param tokenHash the persisted SHA-256 hash
   * @param subjectId the persisted subject
   * @param subjectType the persisted subject type
   * @param familyId the persisted rotation family
   * @param expiresAt the persisted family expiry
   * @param revokedAt when the row was revoked, or {@code null} if still usable
   * @param createdAt when the row was created
   * @return the rebuilt refresh token
   */
  public static RefreshToken reconstitute(
      RefreshTokenId id,
      byte[] tokenHash,
      UUID subjectId,
      SubjectType subjectType,
      UUID familyId,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {
    return new RefreshToken(
        id, tokenHash.clone(), subjectId, subjectType, familyId, expiresAt, revokedAt, createdAt);
  }

  /**
   * Produces the child row of a rotation (auth.md, rule 3.5): same family and the same {@code
   * expiresAt}, copied rather than extended (ADR-008) — the invariant no {@code CHECK} can hold.
   *
   * @param newId identifier for the new row
   * @param newTokenHash SHA-256 of the newly generated plaintext token
   * @return the new, not-yet-persisted child row
   */
  public RefreshToken rotate(RefreshTokenId newId, byte[] newTokenHash) {
    LOGGER.debug("rotate id={} -> newId={}", id, newId);
    return new RefreshToken(
        newId, newTokenHash.clone(), subjectId, subjectType, familyId, expiresAt, null, null);
  }

  /**
   * Marks this row revoked, e.g. because it was just rotated away or the session was closed.
   *
   * @param now the revocation instant
   */
  public void revoke(Instant now) {
    LOGGER.debug("revoke id={}", id);
    this.revokedAt = now;
  }

  /**
   * @param now the instant to check against
   * @return whether the family's absolute expiry has passed
   */
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  /**
   * @return whether this row has already been revoked
   */
  public boolean isRevoked() {
    return revokedAt != null;
  }

  /**
   * @return a new, high-entropy plaintext token, Base64 URL-safe encoded (ADR-005: not a password,
   *     so no Argon2id here — a server-generated, high-entropy value only needs a fast,
   *     deterministic hash)
   */
  public static String generatePlaintext() {
    byte[] bytes = new byte[PLAINTEXT_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * @param plaintext the token to hash
   * @return the SHA-256 hash, ready to store in or compare against {@code
   *     refresh_tokens.token_hash} (ADR-005)
   */
  public static byte[] hash(String plaintext) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(plaintext.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * @return the application-generated identifier
   */
  public RefreshTokenId id() {
    return id;
  }

  /**
   * @return the SHA-256 hash of the plaintext token
   */
  public byte[] tokenHash() {
    return tokenHash.clone();
  }

  /**
   * @return the customer or admin this session belongs to
   */
  public UUID subjectId() {
    return subjectId;
  }

  /**
   * @return {@code CUSTOMER} or {@code ADMIN}
   */
  public SubjectType subjectType() {
    return subjectType;
  }

  /**
   * @return the rotation family this row belongs to
   */
  public UUID familyId() {
    return familyId;
  }

  /**
   * @return the family's absolute expiry, shared by every row descended from the same login
   */
  public Instant expiresAt() {
    return expiresAt;
  }

  /**
   * @return when this row was revoked, or {@code null} if still usable
   */
  public Instant revokedAt() {
    return revokedAt;
  }

  /**
   * @return when this row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }
}
