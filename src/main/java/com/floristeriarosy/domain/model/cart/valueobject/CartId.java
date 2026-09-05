package com.floristeriarosy.domain.model.cart.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Cart} (never assigned by the database). */
public final class CartId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private CartId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a cart being created
   */
  public static CartId newId() {
    return new CartId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static CartId of(UUID value) {
    return new CartId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CartId cartId && value.equals(cartId.value);
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
