package com.floristeriarosy.domain.model.auth.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-generated identifier of a {@code RefreshToken} row (never assigned by the database).
 */
public final class RefreshTokenId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private RefreshTokenId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a refresh token row being created
   */
  public static RefreshTokenId newId() {
    return new RefreshTokenId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static RefreshTokenId of(UUID value) {
    return new RefreshTokenId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof RefreshTokenId refreshTokenId && value.equals(refreshTokenId.value);
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
