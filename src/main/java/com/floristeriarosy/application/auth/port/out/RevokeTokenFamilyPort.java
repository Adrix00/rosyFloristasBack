package com.floristeriarosy.application.auth.port.out;

import java.util.UUID;

/**
 * Revokes every live {@code refresh_tokens} row of a subject (ADR-008). Lives in {@code auth.md} —
 * admin.md calls it whenever an administrator's password or TOTP is reset, or the admin is
 * deactivated (rules 3.4, 3.5, 3.6), so any session an intruder or a lost device still held stops
 * working.
 */
public interface RevokeTokenFamilyPort {

  /**
   * @param subjectId the customer or admin user whose refresh token families are revoked
   */
  void revokeAllForSubject(UUID subjectId);
}
