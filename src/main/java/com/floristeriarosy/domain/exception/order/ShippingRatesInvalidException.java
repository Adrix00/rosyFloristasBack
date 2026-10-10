package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/**
 * The resulting set of active shipping bands breaks a rule of order.md §3.7: none active, the first
 * does not start at 0 km, or there is a gap or an overlap. The message says which rule; it carries
 * distances and prices only, never personal data (order.md, §9).
 */
public final class ShippingRatesInvalidException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message which rule the resulting set breaks
   */
  public ShippingRatesInvalidException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#SHIPPING_RATES_INVALID} */
  @Override
  public String errorCode() {
    return OrderErrorCode.SHIPPING_RATES_INVALID.name();
  }
}
