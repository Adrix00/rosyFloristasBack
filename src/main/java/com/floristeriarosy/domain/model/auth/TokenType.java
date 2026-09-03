package com.floristeriarosy.domain.model.auth;

/**
 * The {@code typ} claim of an issued JWT (auth.md, section 3.3). {@code MFA} identifies an
 * ephemeral, 5-minute token that only proves a correct email/password pair was presented; it grants
 * no access on its own until {@code VerifyAdminMfaUseCase} exchanges it for {@code ACCESS}.
 */
public enum TokenType {
  ACCESS,
  MFA
}
