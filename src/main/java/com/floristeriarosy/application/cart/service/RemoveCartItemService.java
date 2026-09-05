package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.RemoveCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.RemoveCartItemUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartDtoAssembler;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link RemoveCartItemUseCase}: removes a single line. */
@Service
@Transactional
public class RemoveCartItemService implements RemoveCartItemUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoveCartItemService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;
  private final CartPricingPort cartPricingPort;

  /**
   * @param cartReadPort resolves the caller's existing cart
   * @param cartWritePort renews the cart's expiry
   * @param cartItemWritePort deletes the touched line
   * @param cartPricingPort prices the resulting cart for the response
   */
  public RemoveCartItemService(
      CartReadPort cartReadPort,
      CartWritePort cartWritePort,
      CartItemWritePort cartItemWritePort,
      CartPricingPort cartPricingPort) {
    this.cartReadPort = cartReadPort;
    this.cartWritePort = cartWritePort;
    this.cartItemWritePort = cartItemWritePort;
    this.cartPricingPort = cartPricingPort;
  }

  /**
   * @param command the product to remove, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartItemNotFoundException the product has no line in the cart
   */
  @Override
  public CartDto execute(RemoveCartItemCommand command) {
    LOGGER.debug(
        "removeCartItem hasCustomer={} productId={}", command.customerId() != null, command.productId());

    ProductId productId = ProductId.of(command.productId());
    Cart cart =
        CartFinder.find(cartReadPort, command.customerId(), command.sessionToken())
            .orElseThrow(
                () -> new CartItemNotFoundException("No cart line for product " + productId));

    cart.removeItem(productId);
    cartItemWritePort.delete(cart.id(), productId);
    cart.renewExpiry();
    cartWritePort.touch(cart.id(), cart.expiresAt());

    CartDto result = CartDtoAssembler.assemble(cart, cartPricingPort);
    LOGGER.debug("removeCartItem -> cartId={} itemCount={}", result.id(), result.itemCount());
    return result;
  }
}
