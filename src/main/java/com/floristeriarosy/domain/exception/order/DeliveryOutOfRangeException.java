package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.UnprocessableException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The delivery address is more than the delivery radius from the store — no shipping band applies
 * (order.md, §3.7, §9). Carries {@code maxDistanceKm} so the frontend can explain the limit and
 * offer pickup; never the address, its coordinates or its distance.
 */
public final class DeliveryOutOfRangeException extends UnprocessableException
    implements HasErrorCode, HasErrorDetails {

  private final BigDecimal maxDistanceKm;

  /**
   * @param message what the caller should see
   * @param maxDistanceKm the delivery radius, in kilometres
   */
  public DeliveryOutOfRangeException(String message, BigDecimal maxDistanceKm) {
    super(message);
    this.maxDistanceKm = maxDistanceKm;
  }

  /** @return {@link OrderErrorCode#DELIVERY_OUT_OF_RANGE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_OUT_OF_RANGE.name();
  }

  /** @return {@code maxDistanceKm}, the delivery radius */
  @Override
  public Map<String, Object> errorDetails() {
    return Map.of("maxDistanceKm", maxDistanceKm);
  }
}
