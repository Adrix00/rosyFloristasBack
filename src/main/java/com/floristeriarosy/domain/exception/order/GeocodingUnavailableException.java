package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.BadGatewayException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * The delivery address could not be checked at all: Google Maps denied the request, is over its
 * quota, timed out or answered something unreadable (order.md, §3.7, §9). Distinct from {@link
 * DeliveryAddressNotGeocodableException}, which says the address is not real: here nothing is known
 * about the address and the customer can simply retry.
 */
public final class GeocodingUnavailableException extends BadGatewayException implements HasErrorCode {

  private static final String MESSAGE = "The delivery address could not be checked right now";

  /** The message is fixed so no Google text, URL or key can ever reach the API client. */
  public GeocodingUnavailableException() {
    super(MESSAGE);
  }

  /** @return {@link OrderErrorCode#DELIVERY_GEOCODING_UNAVAILABLE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_GEOCODING_UNAVAILABLE.name();
  }
}
