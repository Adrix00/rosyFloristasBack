package com.floristeriarosy.domain.model.product.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Product} (never assigned by the database). */
public final class ProductId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private ProductId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a product being created
   */
  public static ProductId newId() {
    return new ProductId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static ProductId of(UUID value) {
    return new ProductId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ProductId productId && value.equals(productId.value);
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
