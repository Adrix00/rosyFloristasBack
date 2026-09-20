package com.floristeriarosy.infrastructure.payment.redsys;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/**
 * Maps the business {@link OrderNumber} to the 12-digit numeric identifier Redsys expects as
 * {@code Ds_Merchant_Order} (ADR-017 amendment). Redsys requires at most 12 positions and the
 * first 4 to be numeric; the order sequence is already globally unique (order.md §3.1), so padding
 * it to 12 digits preserves uniqueness without a new column.
 *
 * <p>The source format is {@code {CHANNEL}-{YYYYMMDD}-{6-digit sequence}}. Any prefix before the
 * last hyphen is ignored; only the trailing sequence matters.
 */
public final class RedsysOrderId {

  private static final int REDSYS_ORDER_LENGTH = 12;

  private RedsysOrderId() {}

  /**
   * @param orderNumber the business order number, e.g. {@code WEB-20260826-000001}
   * @return the 12-digit zero-padded sequence, e.g. {@code 000000000001}
   * @throws IllegalArgumentException the order number does not end with a numeric sequence
   */
  public static String from(OrderNumber orderNumber) {
    String value = orderNumber.value();
    int lastDash = value.lastIndexOf('-');
    if (lastDash == -1 || lastDash == value.length() - 1) {
      throw new IllegalArgumentException("Order number has no sequence part: " + value);
    }
    String sequence = value.substring(lastDash + 1);
    if (!sequence.matches("\\d+")) {
      throw new IllegalArgumentException("Order number sequence is not numeric: " + value);
    }
    return String.format("%0" + REDSYS_ORDER_LENGTH + "d", Long.parseLong(sequence));
  }
}
