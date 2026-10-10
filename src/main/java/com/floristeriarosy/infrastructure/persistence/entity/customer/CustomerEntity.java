package com.floristeriarosy.infrastructure.persistence.entity.customer;

import com.floristeriarosy.domain.model.customer.CustomerStatus;
import com.floristeriarosy.domain.model.customer.CustomerType;
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

/**
 * JPA mapping of the {@code customers} table for the minimal guest-linking slice (customer.md,
 * section 1/8). Carries {@code @Version} (ADR-009).
 */
@Entity
@Table(name = "customers")
public class CustomerEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomerEntity.class);

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CustomerType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CustomerStatus status;

  @Column(name = "email_encrypted")
  private byte[] emailEncrypted;

  @Column(name = "email_hash")
  private byte[] emailHash;

  @Column(name = "phone_encrypted")
  private byte[] phoneEncrypted;

  @Column(name = "phone_hash")
  private byte[] phoneHash;

  @Column(name = "first_name_encrypted")
  private byte[] firstNameEncrypted;

  @Column(name = "last_name_encrypted")
  private byte[] lastNameEncrypted;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "anonymized_at")
  private Instant anonymizedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected CustomerEntity() {}

  /**
   * @param id the primary key
   * @param type {@code GUEST} or {@code REGISTERED}
   * @param status {@code ACTIVE} or {@code ARCHIVED}
   * @param emailEncrypted the encrypted email, or {@code null} if purged
   * @param emailHash the email HMAC, or {@code null} if purged
   * @param phoneEncrypted the encrypted phone, or {@code null} if purged
   * @param phoneHash the phone HMAC, or {@code null} if purged
   * @param firstNameEncrypted the encrypted first name, or {@code null} if purged
   * @param lastNameEncrypted the encrypted last name, or {@code null} if purged
   * @param passwordHash the Argon2id password hash, or {@code null} for guests
   * @param emailVerifiedAt when the email was verified, or {@code null}
   * @param anonymizedAt when the customer was archived, or {@code null}
   * @param version the optimistic-locking version
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted entity
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted entity
   */
  public CustomerEntity(
      UUID id,
      CustomerType type,
      CustomerStatus status,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] phoneEncrypted,
      byte[] phoneHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      String passwordHash,
      Instant emailVerifiedAt,
      Instant anonymizedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.type = type;
    this.status = status;
    this.emailEncrypted = emailEncrypted == null ? null : emailEncrypted.clone();
    this.emailHash = emailHash == null ? null : emailHash.clone();
    this.phoneEncrypted = phoneEncrypted == null ? null : phoneEncrypted.clone();
    this.phoneHash = phoneHash == null ? null : phoneHash.clone();
    this.firstNameEncrypted = firstNameEncrypted == null ? null : firstNameEncrypted.clone();
    this.lastNameEncrypted = lastNameEncrypted == null ? null : lastNameEncrypted.clone();
    this.passwordHash = passwordHash;
    this.emailVerifiedAt = emailVerifiedAt;
    this.anonymizedAt = anonymizedAt;
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

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return {@code GUEST} or {@code REGISTERED} */
  public CustomerType getType() {
    return type;
  }

  /** @param type the new type */
  public void setType(CustomerType type) {
    this.type = type;
  }

  /** @return {@code ACTIVE} or {@code ARCHIVED} */
  public CustomerStatus getStatus() {
    return status;
  }

  /** @param status the new status */
  public void setStatus(CustomerStatus status) {
    this.status = status;
  }

  /** @return the encrypted email, or {@code null} if purged */
  public byte[] getEmailEncrypted() {
    return emailEncrypted == null ? null : emailEncrypted.clone();
  }

  /** @param emailEncrypted the encrypted email, or {@code null} to purge */
  public void setEmailEncrypted(byte[] emailEncrypted) {
    this.emailEncrypted = emailEncrypted == null ? null : emailEncrypted.clone();
  }

  /** @return the email HMAC, or {@code null} if purged */
  public byte[] getEmailHash() {
    return emailHash == null ? null : emailHash.clone();
  }

  /** @param emailHash the email HMAC, or {@code null} to purge */
  public void setEmailHash(byte[] emailHash) {
    this.emailHash = emailHash == null ? null : emailHash.clone();
  }

  /** @return the encrypted phone, or {@code null} if purged */
  public byte[] getPhoneEncrypted() {
    return phoneEncrypted == null ? null : phoneEncrypted.clone();
  }

  /** @param phoneEncrypted the encrypted phone, or {@code null} to purge */
  public void setPhoneEncrypted(byte[] phoneEncrypted) {
    this.phoneEncrypted = phoneEncrypted == null ? null : phoneEncrypted.clone();
  }

  /** @return the phone HMAC, or {@code null} if purged */
  public byte[] getPhoneHash() {
    return phoneHash == null ? null : phoneHash.clone();
  }

  /** @param phoneHash the phone HMAC, or {@code null} to purge */
  public void setPhoneHash(byte[] phoneHash) {
    this.phoneHash = phoneHash == null ? null : phoneHash.clone();
  }

  /** @return the encrypted first name, or {@code null} if purged */
  public byte[] getFirstNameEncrypted() {
    return firstNameEncrypted == null ? null : firstNameEncrypted.clone();
  }

  /** @param firstNameEncrypted the encrypted first name, or {@code null} to purge */
  public void setFirstNameEncrypted(byte[] firstNameEncrypted) {
    this.firstNameEncrypted = firstNameEncrypted == null ? null : firstNameEncrypted.clone();
  }

  /** @return the encrypted last name, or {@code null} if purged */
  public byte[] getLastNameEncrypted() {
    return lastNameEncrypted == null ? null : lastNameEncrypted.clone();
  }

  /** @param lastNameEncrypted the encrypted last name, or {@code null} to purge */
  public void setLastNameEncrypted(byte[] lastNameEncrypted) {
    this.lastNameEncrypted = lastNameEncrypted == null ? null : lastNameEncrypted.clone();
  }

  /** @return the Argon2id password hash, or {@code null} for guests */
  public String getPasswordHash() {
    return passwordHash;
  }

  /** @return when the email was verified, or {@code null} */
  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  /** @return when the customer was archived, or {@code null} */
  public Instant getAnonymizedAt() {
    return anonymizedAt;
  }

  /** @return the optimistic-locking version (ADR-009) */
  public long getVersion() {
    return version;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
