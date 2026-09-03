package com.floristeriarosy.domain.model.admin;

import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aggregate root of the admin module (admin.md). Never carries a plaintext email or password. */
public final class Admin {

  private static final Logger LOGGER = LoggerFactory.getLogger(Admin.class);

  private final AdminId id;
  private byte[] emailEncrypted;
  private byte[] emailHash;
  private String passwordHash;
  private AdminRole role;
  private byte[] totpSecretEncrypted;
  private boolean totpEnabled;
  private Long totpLastUsedStep;
  private boolean passwordChangeRequired;
  private boolean active;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private Admin(
      AdminId id,
      byte[] emailEncrypted,
      byte[] emailHash,
      String passwordHash,
      AdminRole role,
      byte[] totpSecretEncrypted,
      boolean totpEnabled,
      Long totpLastUsedStep,
      boolean passwordChangeRequired,
      boolean active,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.emailEncrypted = emailEncrypted;
    this.emailHash = emailHash;
    this.passwordHash = passwordHash;
    this.role = role;
    this.totpSecretEncrypted = totpSecretEncrypted;
    this.totpEnabled = totpEnabled;
    this.totpLastUsedStep = totpLastUsedStep;
    this.passwordChangeRequired = passwordChangeRequired;
    this.active = active;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * New administrator (admin.md, rule 3.2). Born {@code active}, with TOTP not yet enrolled and a
   * provisional password that must be changed on first use. Timestamps are left {@code null} — the
   * database sets them on insert.
   *
   * @param id application-generated identifier
   * @param emailEncrypted the email, already encrypted (ADR-005)
   * @param emailHash the email's HMAC, for the {@code UNIQUE} lookup
   * @param passwordHash the provisional password, already Argon2id-hashed
   * @param role {@code OWNER} or {@code ADMIN}
   * @return the new, not-yet-persisted admin
   */
  public static Admin create(
      AdminId id, byte[] emailEncrypted, byte[] emailHash, String passwordHash, AdminRole role) {
    LOGGER.debug("create id={} role={}", id, role);
    Admin result =
        new Admin(
            id,
            emailEncrypted,
            emailHash,
            passwordHash,
            role,
            null,
            false,
            null,
            true,
            true,
            0L,
            null,
            null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds an admin from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param emailEncrypted the persisted encrypted email
   * @param emailHash the persisted email HMAC
   * @param passwordHash the persisted password hash
   * @param role the persisted role
   * @param totpSecretEncrypted the persisted encrypted TOTP secret, or {@code null}
   * @param totpEnabled whether TOTP is enrolled
   * @param totpLastUsedStep the last accepted TOTP step, or {@code null}
   * @param passwordChangeRequired whether the current password is still provisional
   * @param active whether the admin can currently log in
   * @param version the optimistic-locking version (ADR-009)
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt admin
   */
  public static Admin reconstitute(
      AdminId id,
      byte[] emailEncrypted,
      byte[] emailHash,
      String passwordHash,
      AdminRole role,
      byte[] totpSecretEncrypted,
      boolean totpEnabled,
      Long totpLastUsedStep,
      boolean passwordChangeRequired,
      boolean active,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Admin(
        id,
        emailEncrypted,
        emailHash,
        passwordHash,
        role,
        totpSecretEncrypted,
        totpEnabled,
        totpLastUsedStep,
        passwordChangeRequired,
        active,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * {@code PUT}: replaces email and role (admin.md, section 5). Only the {@code OWNER} calls this,
   * and never on themselves for the email (admin.md, rule 3.3).
   *
   * @param emailEncrypted the new encrypted email
   * @param emailHash the new email's HMAC
   * @param role the new role
   */
  public void replace(byte[] emailEncrypted, byte[] emailHash, AdminRole role) {
    LOGGER.debug("replace id={} role={}", id, role);
    this.emailEncrypted = emailEncrypted.clone();
    this.emailHash = emailHash.clone();
    this.role = role;
    LOGGER.debug("replace id={} -> replaced", id);
  }

  /** Reactivates the admin, unchanged otherwise: same role, password and TOTP (admin.md, 3.6). */
  public void activate() {
    LOGGER.debug("activate id={}", id);
    this.active = true;
  }

  /** Deactivates the admin. The caller is responsible for revoking its sessions (admin.md, 3.6). */
  public void deactivate() {
    LOGGER.debug("deactivate id={}", id);
    this.active = false;
  }

  /**
   * Sets a new provisional password, fixed by the {@code OWNER} (admin.md, rule 3.4). The caller is
   * responsible for revoking the admin's sessions.
   *
   * @param newPasswordHash the new provisional password, already Argon2id-hashed
   */
  public void resetPassword(String newPasswordHash) {
    LOGGER.debug("resetPassword id={}", id);
    this.passwordHash = newPasswordHash;
    this.passwordChangeRequired = true;
  }

  /**
   * Sets a new password chosen by the admin themselves (admin.md, rule 3.4): no longer provisional.
   *
   * @param newPasswordHash the new password, already Argon2id-hashed
   */
  public void changeOwnPassword(String newPasswordHash) {
    LOGGER.debug("changeOwnPassword id={}", id);
    this.passwordHash = newPasswordHash;
    this.passwordChangeRequired = false;
  }

  /**
   * Stores a freshly generated TOTP secret pending confirmation (auth.md, rule 3.4). {@code
   * totpEnabled} stays {@code false} until {@code VerifyAdminMfaService} confirms a valid code.
   * Calling this again before confirming overwrites the secret — the QR code just scanned stops
   * working, which is exactly what a lost-phone retry needs.
   *
   * @param totpSecretEncrypted the new secret, already encrypted (ADR-005)
   */
  public void enrollTotp(byte[] totpSecretEncrypted) {
    LOGGER.debug("enrollTotp id={}", id);
    this.totpSecretEncrypted = totpSecretEncrypted.clone();
  }

  /**
   * Confirms a pending TOTP enrollment (auth.md, rule 3.4): the admin proved they hold a valid
   * code, so the second factor is now active.
   *
   * @param totpLastUsedStep the step of the code that confirmed enrollment, so it cannot be
   *     replayed
   */
  public void confirmTotp(long totpLastUsedStep) {
    LOGGER.debug("confirmTotp id={}", id);
    this.totpEnabled = true;
    this.totpLastUsedStep = totpLastUsedStep;
  }

  /**
   * Resets TOTP to its initial state, so the next login re-enrolls it (admin.md, rule 3.5). The
   * password is untouched — two independent factors. The caller is responsible for revoking the
   * admin's sessions.
   */
  public void resetTotp() {
    LOGGER.debug("resetTotp id={}", id);
    this.totpSecretEncrypted = null;
    this.totpEnabled = false;
    this.totpLastUsedStep = null;
  }

  /**
   * @return the application-generated identifier
   */
  public AdminId id() {
    return id;
  }

  /**
   * @return the encrypted email
   */
  public byte[] emailEncrypted() {
    return emailEncrypted.clone();
  }

  /**
   * @return the email's HMAC
   */
  public byte[] emailHash() {
    return emailHash.clone();
  }

  /**
   * @return the Argon2id password hash
   */
  public String passwordHash() {
    return passwordHash;
  }

  /**
   * @return {@code OWNER} or {@code ADMIN}
   */
  public AdminRole role() {
    return role;
  }

  /**
   * @return the encrypted TOTP secret, or {@code null} if not enrolled
   */
  public byte[] totpSecretEncrypted() {
    return totpSecretEncrypted == null ? null : totpSecretEncrypted.clone();
  }

  /**
   * @return whether TOTP is enrolled
   */
  public boolean totpEnabled() {
    return totpEnabled;
  }

  /**
   * @return the last accepted TOTP step, or {@code null}
   */
  public Long totpLastUsedStep() {
    return totpLastUsedStep;
  }

  /**
   * @return whether the current password is still provisional and must be changed
   */
  public boolean passwordChangeRequired() {
    return passwordChangeRequired;
  }

  /**
   * @return whether the admin can currently log in
   */
  public boolean active() {
    return active;
  }

  /**
   * @return the optimistic-locking version (ADR-009)
   */
  public long version() {
    return version;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
