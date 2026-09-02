package com.floristeriarosy.infrastructure.persistence.entity.admin;

import com.floristeriarosy.domain.model.admin.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JPA mapping of the {@code admin_users} table. Carries {@code @Version} (ADR-009). */
@Entity
@Table(name = "admin_users")
public class AdminUserEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserEntity.class);

  @Id private UUID id;

  @Column(name = "email_encrypted", nullable = false)
  private byte[] emailEncrypted;

  @Column(name = "email_hash", nullable = false)
  private byte[] emailHash;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdminRole role;

  @Column(name = "totp_secret_encrypted")
  private byte[] totpSecretEncrypted;

  @Column(name = "totp_enabled", nullable = false)
  private boolean totpEnabled;

  @Column(name = "totp_last_used_step")
  private Long totpLastUsedStep;

  @Column(name = "password_change_required", nullable = false)
  private boolean passwordChangeRequired;

  @Column(nullable = false)
  private boolean active;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected AdminUserEntity() {}

  /**
   * @param id the primary key
   * @param emailEncrypted the encrypted email
   * @param emailHash the email's HMAC
   * @param passwordHash the Argon2id password hash
   * @param role {@code OWNER} or {@code ADMIN}
   * @param totpSecretEncrypted the encrypted TOTP secret, or {@code null}
   * @param totpEnabled whether TOTP is enrolled
   * @param totpLastUsedStep the last accepted TOTP step, or {@code null}
   * @param passwordChangeRequired whether the current password is still provisional
   * @param active whether the admin can currently log in
   * @param version the optimistic-locking version, as previously loaded (0 for a new row)
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted admin
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted admin
   */
  public AdminUserEntity(
      UUID id,
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
    this.emailEncrypted = emailEncrypted.clone();
    this.emailHash = emailHash.clone();
    this.passwordHash = passwordHash;
    this.role = role;
    this.totpSecretEncrypted = totpSecretEncrypted == null ? null : totpSecretEncrypted.clone();
    this.totpEnabled = totpEnabled;
    this.totpLastUsedStep = totpLastUsedStep;
    this.passwordChangeRequired = passwordChangeRequired;
    this.active = active;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Sets {@code createdAt}/{@code updatedAt} in application code, since {@code V1} only gives
   * {@code created_at} a DB-side {@code DEFAULT now()} and nothing updates {@code updated_at} on
   * its own.
   */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    LOGGER.debug("onCreate id={} createdAt={}", id, createdAt);
  }

  /** Refreshes {@code updatedAt} on every update; see {@link #onCreate()}. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
    LOGGER.debug("onUpdate id={} updatedAt={}", id, updatedAt);
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the encrypted email
   */
  public byte[] getEmailEncrypted() {
    return emailEncrypted.clone();
  }

  /**
   * @return the email's HMAC
   */
  public byte[] getEmailHash() {
    return emailHash.clone();
  }

  /**
   * @return the Argon2id password hash
   */
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * @return {@code OWNER} or {@code ADMIN}
   */
  public AdminRole getRole() {
    return role;
  }

  /**
   * @return the encrypted TOTP secret, or {@code null}
   */
  public byte[] getTotpSecretEncrypted() {
    return totpSecretEncrypted == null ? null : totpSecretEncrypted.clone();
  }

  /**
   * @return whether TOTP is enrolled
   */
  public boolean isTotpEnabled() {
    return totpEnabled;
  }

  /**
   * @return the last accepted TOTP step, or {@code null}
   */
  public Long getTotpLastUsedStep() {
    return totpLastUsedStep;
  }

  /**
   * @return whether the current password is still provisional
   */
  public boolean isPasswordChangeRequired() {
    return passwordChangeRequired;
  }

  /**
   * @return whether the admin can currently log in
   */
  public boolean isActive() {
    return active;
  }

  /**
   * @return the optimistic-locking version (ADR-009)
   */
  public long getVersion() {
    return version;
  }

  /**
   * @return when the row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
