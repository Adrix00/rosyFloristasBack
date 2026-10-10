package com.floristeriarosy.domain.model.order.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code ShippingBand} (never assigned by the database). */
public final class ShippingBandId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private ShippingBandId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a band being created
   */
  public static ShippingBandId newId() {
    return new ShippingBandId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static ShippingBandId of(UUID value) {
    return new ShippingBandId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ShippingBandId shippingBandId && value.equals(shippingBandId.value);
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
