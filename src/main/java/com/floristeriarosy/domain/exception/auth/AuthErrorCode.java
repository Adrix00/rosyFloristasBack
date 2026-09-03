package com.floristeriarosy.domain.exception.auth;

/** Business error codes published by the auth module (ADR-012, auth.md section 9). */
public enum AuthErrorCode {
  INVALID_CREDENTIALS,
  EMAIL_NOT_VERIFIED,
  INVALID_REFRESH_TOKEN,
  TOKEN_EXPIRED,
  SESSION_REVOKED,
  INVALID_MFA_TOKEN,
  INVALID_TOTP_CODE,
  TOTP_ENROLLMENT_REQUIRED,
  TOTP_ALREADY_ENROLLED,
  PASSWORD_CHANGE_REQUIRED,
  AUTH_VALIDATION_FAILED
}
