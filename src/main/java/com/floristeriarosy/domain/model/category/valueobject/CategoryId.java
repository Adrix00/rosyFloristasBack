package com.floristeriarosy.domain.model.category.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Category} (never assigned by the database). */
public final class CategoryId {

  private final UUID value;

  private CategoryId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  public static CategoryId newId() {
    return new CategoryId(UUID.randomUUID());
  }

  public static CategoryId of(UUID value) {
    return new CategoryId(value);
  }

  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CategoryId categoryId && value.equals(categoryId.value);
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
