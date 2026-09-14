package com.floristeriarosy.domain.model.customer.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Customer} (never assigned by the database). */
public final class CustomerId {

  private final UUID value;

  private CustomerId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /** @return a new, random identifier */
  public static CustomerId newId() {
    return new CustomerId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value
   * @return the wrapped identifier
   */
  public static CustomerId of(UUID value) {
    return new CustomerId(value);
  }

  /** @return the raw UUID value */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CustomerId customerId && value.equals(customerId.value);
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
