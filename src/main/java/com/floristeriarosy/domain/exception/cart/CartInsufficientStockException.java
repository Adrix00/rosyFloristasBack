package com.floristeriarosy.domain.exception.cart;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.UnprocessableException;
import java.util.Map;

/**
 * The requested quantity is more than the managed stock available (cart.md, rule 3.3): a soft
 * check, it reserves nothing. Carries the real {@code availableQuantity} so the client can show it
 * without a second call.
 */
public final class CartInsufficientStockException extends UnprocessableException
    implements HasErrorCode, HasErrorDetails {

  private final int availableQuantity;

  /**
   * @param message a message for a person; never exposed raw to the API client
   * @param availableQuantity the real stock available for the product
   */
  public CartInsufficientStockException(String message, int availableQuantity) {
    super(message);
    this.availableQuantity = availableQuantity;
  }

  @Override
  public String errorCode() {
    return CartErrorCode.CART_INSUFFICIENT_STOCK.name();
  }

  @Override
  public Map<String, Object> errorDetails() {
    return Map.of("availableQuantity", availableQuantity);
  }
}
