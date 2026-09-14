package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** Google Maps could not locate the delivery address (order.md, §3.7, §9). */
public final class DeliveryAddressNotGeocodableException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public DeliveryAddressNotGeocodableException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#DELIVERY_ADDRESS_NOT_GEOCODABLE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_ADDRESS_NOT_GEOCODABLE.name();
  }
}
