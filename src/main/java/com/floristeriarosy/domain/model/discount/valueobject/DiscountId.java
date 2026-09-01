package com.floristeriarosy.domain.model.discount.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Discount} (never assigned by the database). */
public final class DiscountId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private DiscountId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a discount being created
   */
  public static DiscountId newId() {
    return new DiscountId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static DiscountId of(UUID value) {
    return new DiscountId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DiscountId discountId && value.equals(discountId.value);
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
