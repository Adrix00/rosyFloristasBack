package com.floristeriarosy.domain.model.attribute.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code AttributeDefinition} (never assigned by the database). */
public final class AttributeDefinitionId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private AttributeDefinitionId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an attribute definition being created
   */
  public static AttributeDefinitionId newId() {
    return new AttributeDefinitionId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static AttributeDefinitionId of(UUID value) {
    return new AttributeDefinitionId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AttributeDefinitionId attributeDefinitionId
        && value.equals(attributeDefinitionId.value);
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
