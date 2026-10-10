package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.UnprocessableException;
import java.util.Map;

/** Fase 1: a line's stock ran out between the cart check and the reservation (order.md, §9). */
public final class CheckoutInsufficientStockException extends UnprocessableException
    implements HasErrorCode, HasErrorDetails {

  private final int availableQuantity;

  /**
   * @param message what the caller should see
   * @param availableQuantity how many units remain
   */
  public CheckoutInsufficientStockException(String message, int availableQuantity) {
    super(message);
    this.availableQuantity = availableQuantity;
  }

  /** @return {@link OrderErrorCode#CHECKOUT_INSUFFICIENT_STOCK} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_INSUFFICIENT_STOCK.name();
  }

  /** @return {@code {"availableQuantity": ...}} */
  @Override
  public Map<String, Object> errorDetails() {
    return Map.of("availableQuantity", availableQuantity);
  }
}
