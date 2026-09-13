package com.floristeriarosy.domain.model.order.valueobject;

/**
 * The application-generated business identifier of an order (order.md, rule 3.1): {@code
 * {CHANNEL}-{YYYYMMDD}-{6-digit sequence}}, e.g. {@code WEB-20260826-000001}. Built from {@code
 * order_number_seq} outside the domain (a sequence read is JDBC, ADR-002) — this type only wraps
 * and validates the already-formatted value.
 */
public final class OrderNumber {

  private final String value;

  /**
   * @param value the formatted order number
   * @throws IllegalArgumentException {@code value} is {@code null} or blank
   */
  private OrderNumber(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    this.value = value;
  }

  /**
   * @param value the formatted order number, already built from {@code order_number_seq}
   * @return the order number wrapping it
   */
  public static OrderNumber of(String value) {
    return new OrderNumber(value);
  }

  /**
   * @return the formatted order number
   */
  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrderNumber orderNumber && value.equals(orderNumber.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
