package com.floristeriarosy.infrastructure.security.jwt;

/**
 * Claim names shared by every class that reads or writes this module's JWTs ({@link
 * NimbusAccessToken}, {@link AccessTypeJwtValidator}, {@code PasswordChangeRequiredFilter}).
 * Centralized because a typo in one of these strings in only one of those classes would silently
 * defeat the {@code typ = "access"} check or the password-change gate, with no compiler error.
 */
public final class AccessTokenJwtClaims {

  /** {@code "access"} or {@code "mfa"} (auth.md, rule 3.3). */
  public static final String TYPE = "typ";

  /** The value {@link #TYPE} carries on a fully-issued session token. */
  public static final String ACCESS_TYPE_VALUE = "access";

  /** {@code CUSTOMER} or {@code ADMIN}; absent on an {@code mfa} token. */
  public static final String SUBJECT_TYPE = "subject_type";

  /** {@code OWNER} or {@code ADMIN}; absent on an {@code mfa} token. */
  public static final String ROLE = "role";

  /** Present and {@code true} only when the session's password is still provisional. */
  public static final String PASSWORD_CHANGE_REQUIRED = "pwd_change_required";

  private AccessTokenJwtClaims() {}
}
