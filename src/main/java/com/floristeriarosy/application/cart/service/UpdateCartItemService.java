package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.UpdateCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.UpdateCartItemUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartDtoAssembler;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.application.cart.support.CartOptimisticRetry;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateCartItemUseCase}: fixes a line's quantity to an exact value. */
@Service
@Transactional
public class UpdateCartItemService implements UpdateCartItemUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCartItemService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;
  private final CartPricingPort cartPricingPort;
  private final CartProductAvailabilityPort availabilityPort;

  /**
   * @param cartReadPort resolves the caller's existing cart
   * @param cartWritePort renews the cart's expiry
   * @param cartItemWritePort persists the touched line
   * @param cartPricingPort prices the resulting cart for the response
   * @param availabilityPort checks the product's real stock
   */
  public UpdateCartItemService(
      CartReadPort cartReadPort,
      CartWritePort cartWritePort,
      CartItemWritePort cartItemWritePort,
      CartPricingPort cartPricingPort,
      CartProductAvailabilityPort availabilityPort) {
    this.cartReadPort = cartReadPort;
    this.cartWritePort = cartWritePort;
    this.cartItemWritePort = cartItemWritePort;
    this.cartPricingPort = cartPricingPort;
    this.availabilityPort = availabilityPort;
  }

  /**
   * @param command the product and the exact quantity to set, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartItemNotFoundException the product has no line in the cart
   * @throws CartInsufficientStockException the requested quantity exceeds real stock (rule 3.3)
   */
  @Override
  public CartDto execute(UpdateCartItemCommand command) {
    LOGGER.debug(
        "updateCartItem hasCustomer={} productId={} quantity={}",
        command.customerId() != null,
        command.productId(),
        command.quantity());

    ProductId productId = ProductId.of(command.productId());
    requireEnoughStock(productId, command.quantity());

    return CartOptimisticRetry.withRetry(() -> doExecute(command, productId));
  }

  /**
   * @param command the product and the exact quantity to set, plus the caller's identity
   * @param productId {@code command.productId()}, already parsed
   * @return the resulting, fully priced cart
   * @throws CartItemNotFoundException the product has no line in the cart
   */
  private CartDto doExecute(UpdateCartItemCommand command, ProductId productId) {
    Cart cart =
        CartFinder.find(cartReadPort, command.customerId(), command.sessionToken())
            .orElseThrow(
                () -> new CartItemNotFoundException("No cart line for product " + productId));

    if (!cart.items().containsKey(productId)) {
      throw new CartItemNotFoundException("No cart line for product " + productId);
    }

    cart.setItemQuantity(productId, command.quantity());
    cart.renewExpiry();
    // Version-guarded write first (ADR-009 amendment): see AddCartItemService.doExecute.
    cartWritePort.touch(cart.id(), cart.expiresAt());
    cartItemWritePort.save(cart.id(), productId, command.quantity());

    CartDto result = CartDtoAssembler.assemble(cart, cartPricingPort);
    LOGGER.debug("updateCartItem -> cartId={} itemCount={}", result.id(), result.itemCount());
    return result;
  }

  /**
   * @param productId the product being updated
   * @param requestedQuantity the exact quantity being set
   * @throws CartInsufficientStockException {@code requestedQuantity} exceeds real stock
   */
  private void requireEnoughStock(ProductId productId, int requestedQuantity) {
    availabilityPort
        .availableStock(productId)
        .filter(stock -> requestedQuantity > stock)
        .ifPresent(
            stock -> {
              throw new CartInsufficientStockException(
                  "Requested quantity " + requestedQuantity + " exceeds available stock", stock);
            });
  }
}
