package com.floristeriarosy.domain.model.inventory.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code InventoryAlert} (never assigned by the database). */
public final class InventoryAlertId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private InventoryAlertId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an inventory alert being created
   */
  public static InventoryAlertId newId() {
    return new InventoryAlertId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static InventoryAlertId of(UUID value) {
    return new InventoryAlertId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InventoryAlertId inventoryAlertId && value.equals(inventoryAlertId.value);
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
