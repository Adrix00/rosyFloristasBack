package com.floristeriarosy.domain.model.customer;

import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the customer module's minimal slice (customer.md, section 1). Only what
 * {@code order.md}'s guest checkout needs (rule 3.2) — registration, addresses, password and admin
 * management belong to the full {@code customer.md} feature, not built here.
 */
public final class Customer {

  private static final Logger LOGGER = LoggerFactory.getLogger(Customer.class);

  private final CustomerId id;
  private final CustomerType type;
  private CustomerStatus status;
  private byte[] emailEncrypted;
  private byte[] emailHash;
  private byte[] firstNameEncrypted;
  private byte[] lastNameEncrypted;
  private byte[] phoneEncrypted;
  private byte[] phoneHash;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private Customer(
      CustomerId id,
      CustomerType type,
      CustomerStatus status,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.type = Objects.requireNonNull(type, "type");
    this.status = Objects.requireNonNull(status, "status");
    this.emailEncrypted = copy(emailEncrypted);
    this.emailHash = copy(emailHash);
    this.firstNameEncrypted = copy(firstNameEncrypted);
    this.lastNameEncrypted = copy(lastNameEncrypted);
    this.phoneEncrypted = copy(phoneEncrypted);
    this.phoneHash = copy(phoneHash);
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * @param value a byte array, or {@code null}
   * @return a defensive copy, or {@code null}
   */
  private static byte[] copy(byte[] value) {
    return value == null ? null : value.clone();
  }

  /**
   * A new {@code GUEST}, born from a sessionless checkout's buyer details (customer.md, rule 3.2).
   *
   * @param id application-generated identifier
   * @param emailEncrypted the buyer's encrypted email
   * @param emailHash the buyer's email HMAC, for the {@code UNIQUE} lookup
   * @param firstNameEncrypted the buyer's encrypted first name
   * @param lastNameEncrypted the buyer's encrypted last name
   * @param phoneEncrypted the buyer's encrypted phone
   * @param phoneHash the buyer's phone HMAC
   * @return the new, {@code ACTIVE} {@code GUEST}
   */
  public static Customer registerGuest(
      CustomerId id,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash) {
    LOGGER.debug("registerGuest id={}", id);
    Customer result =
        new Customer(
            id, CustomerType.GUEST, CustomerStatus.ACTIVE, emailEncrypted, emailHash, firstNameEncrypted,
            lastNameEncrypted, phoneEncrypted, phoneHash, 0L, null, null);
    LOGGER.debug("registerGuest id={} -> registered", id);
    return result;
  }

  /**
   * Rebuilds a customer from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param type {@code GUEST} or {@code REGISTERED}
   * @param status {@code ACTIVE} or {@code ARCHIVED}
   * @param emailEncrypted the persisted encrypted email, or {@code null} if purged
   * @param emailHash the persisted email HMAC, or {@code null} if purged
   * @param firstNameEncrypted the persisted encrypted first name, or {@code null} if purged
   * @param lastNameEncrypted the persisted encrypted last name, or {@code null} if purged
   * @param phoneEncrypted the persisted encrypted phone, or {@code null} if purged
   * @param phoneHash the persisted phone HMAC, or {@code null} if purged
   * @param version the optimistic-locking version (ADR-009)
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt customer
   */
  public static Customer reconstitute(
      CustomerId id,
      CustomerType type,
      CustomerStatus status,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Customer(
        id, type, status, emailEncrypted, emailHash, firstNameEncrypted, lastNameEncrypted, phoneEncrypted,
        phoneHash, version, createdAt, updatedAt);
  }

  /**
   * A returning {@code GUEST}'s contact is refreshed with the latest checkout's details, not
   * preserved — a {@code GUEST} has no profile of its own to protect (customer.md, rule 3.2).
   *
   * @param firstNameEncrypted the latest encrypted first name
   * @param lastNameEncrypted the latest encrypted last name
   * @param phoneEncrypted the latest encrypted phone
   * @param phoneHash the latest phone HMAC
   */
  public void updateGuestContact(
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash) {
    LOGGER.debug("updateGuestContact id={}", id);
    this.firstNameEncrypted = copy(firstNameEncrypted);
    this.lastNameEncrypted = copy(lastNameEncrypted);
    this.phoneEncrypted = copy(phoneEncrypted);
    this.phoneHash = copy(phoneHash);
    LOGGER.debug("updateGuestContact id={} -> updated", id);
  }

  /** @return the application-generated identifier */
  public CustomerId id() {
    return id;
  }

  /** @return {@code GUEST} or {@code REGISTERED} */
  public CustomerType type() {
    return type;
  }

  /** @return {@code ACTIVE} or {@code ARCHIVED} */
  public CustomerStatus status() {
    return status;
  }

  /** @return the encrypted email, or {@code null} if purged */
  public byte[] emailEncrypted() {
    return emailEncrypted == null ? null : emailEncrypted.clone();
  }

  /** @return the email HMAC, or {@code null} if purged */
  public byte[] emailHash() {
    return emailHash == null ? null : emailHash.clone();
  }

  /** @return the encrypted first name, or {@code null} if purged */
  public byte[] firstNameEncrypted() {
    return firstNameEncrypted == null ? null : firstNameEncrypted.clone();
  }

  /** @return the encrypted last name, or {@code null} if purged */
  public byte[] lastNameEncrypted() {
    return lastNameEncrypted == null ? null : lastNameEncrypted.clone();
  }

  /** @return the encrypted phone, or {@code null} if purged */
  public byte[] phoneEncrypted() {
    return phoneEncrypted == null ? null : phoneEncrypted.clone();
  }

  /** @return the phone HMAC, or {@code null} if purged */
  public byte[] phoneHash() {
    return phoneHash == null ? null : phoneHash.clone();
  }

  /** @return the optimistic-locking version (ADR-009) */
  public long version() {
    return version;
  }

  /** @return when the row was created, or {@code null} before the first save */
  public Instant createdAt() {
    return createdAt;
  }

  /** @return when the row was last updated, or {@code null} before the first save */
  public Instant updatedAt() {
    return updatedAt;
  }
}
