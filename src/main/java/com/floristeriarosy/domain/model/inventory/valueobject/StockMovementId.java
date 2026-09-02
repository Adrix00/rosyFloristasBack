package com.floristeriarosy.domain.model.inventory.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code StockMovement} (never assigned by the database). */
public final class StockMovementId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private StockMovementId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a stock movement being created
   */
  public static StockMovementId newId() {
    return new StockMovementId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static StockMovementId of(UUID value) {
    return new StockMovementId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof StockMovementId stockMovementId && value.equals(stockMovementId.value);
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
