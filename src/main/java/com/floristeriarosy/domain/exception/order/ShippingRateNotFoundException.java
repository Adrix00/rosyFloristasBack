package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** {@code PUT /shipping-rates/{id}}: no shipping band with that id (order.md, §9). */
public final class ShippingRateNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public ShippingRateNotFoundException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#SHIPPING_RATE_NOT_FOUND} */
  @Override
  public String errorCode() {
    return OrderErrorCode.SHIPPING_RATE_NOT_FOUND.name();
  }
}
