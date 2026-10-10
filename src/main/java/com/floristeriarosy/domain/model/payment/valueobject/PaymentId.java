package com.floristeriarosy.domain.model.payment.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Payment} (never assigned by the database). */
public final class PaymentId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private PaymentId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a payment being initiated
   */
  public static PaymentId newId() {
    return new PaymentId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static PaymentId of(UUID value) {
    return new PaymentId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PaymentId paymentId && value.equals(paymentId.value);
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
