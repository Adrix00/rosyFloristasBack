package com.floristeriarosy.domain.model.admin.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code Admin} (never assigned by the database). */
public final class AdminId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private AdminId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an admin being created
   */
  public static AdminId newId() {
    return new AdminId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static AdminId of(UUID value) {
    return new AdminId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AdminId adminId && value.equals(adminId.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
