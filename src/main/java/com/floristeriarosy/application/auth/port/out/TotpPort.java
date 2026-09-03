package com.floristeriarosy.application.auth.port.out;

import java.util.Optional;

/**
 * Generates and verifies TOTP codes (RFC 6238). The secret this port hands back and accepts is
 * always Base32-encoded text — the form an authenticator app and a manual-entry field both expect —
 * never raw bytes.
 */
public interface TotpPort {

  /**
   * @return a new, random, Base32-encoded secret (20 bytes of entropy)
   */
  String generateSecret();

  /**
   * Accepts a code within &plusmn;1 step of the current time, and only if the accepted step is
   * strictly greater than {@code lastUsedStep} (auth.md, rule 3.4: a code is never accepted twice).
   *
   * @param secret the admin's Base32-encoded TOTP secret
   * @param code the 6-digit code presented by the caller
   * @param lastUsedStep the last step this secret has already consumed, or {@code null} if none
   * @return the accepted step, to be persisted as the new {@code totp_last_used_step}; empty if the
   *     code is wrong, out of window, or already consumed
   */
  Optional<Long> verify(String secret, String code, Long lastUsedStep);

  /**
   * @param secret the admin's Base32-encoded TOTP secret
   * @param email the admin's email, shown as the account label in the authenticator app
   * @return the {@code otpauth://totp/...} URI for the enrollment QR code
   */
  String otpauthUri(String secret, String email);
}
