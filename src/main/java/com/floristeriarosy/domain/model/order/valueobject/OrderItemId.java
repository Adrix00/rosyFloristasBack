package com.floristeriarosy.domain.model.order.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code OrderItem} (never assigned by the database). */
public final class OrderItemId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private OrderItemId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an order line being created
   */
  public static OrderItemId newId() {
    return new OrderItemId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static OrderItemId of(UUID value) {
    return new OrderItemId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrderItemId orderItemId && value.equals(orderItemId.value);
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
