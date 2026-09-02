package com.floristeriarosy.domain.exception.admin;

/** Business error codes published by the admin module (ADR-012, admin.md section 9). */
public enum AdminErrorCode {
  ADMIN_NOT_FOUND,
  ADMIN_EMAIL_ALREADY_EXISTS,
  LAST_OWNER_CANNOT_BE_REMOVED,
  INVALID_CURRENT_PASSWORD,
  PASSWORD_UNCHANGED,
  ADMIN_VALIDATION_FAILED
}
