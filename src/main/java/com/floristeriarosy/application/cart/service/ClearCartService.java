package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartDtoAssembler;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.domain.model.cart.Cart;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ClearCartUseCase}: empties the cart, never deletes it (cart.md §4). A no-op on
 * an already-empty or nonexistent cart writes nothing (cart.md §10).
 */
@Service
@Transactional
public class ClearCartService implements ClearCartUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClearCartService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;
  private final CartPricingPort cartPricingPort;

  /**
   * @param cartReadPort resolves the caller's existing cart, if any
   * @param cartWritePort renews the cart's expiry
   * @param cartItemWritePort deletes every line
   * @param cartPricingPort prices the resulting cart for the response
   */
  public ClearCartService(
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
   * @param command the caller's identity
   * @return the resulting, empty cart
   */
  @Override
  public CartDto execute(ClearCartCommand command) {
    LOGGER.debug("clearCart hasCustomer={}", command.customerId() != null);

    Optional<Cart> existing = CartFinder.find(cartReadPort, command.customerId(), command.sessionToken());
    if (existing.isEmpty() || existing.get().items().isEmpty()) {
      CartDto result = existing.map(cart -> CartDtoAssembler.assemble(cart, cartPricingPort)).orElseGet(CartDto::empty);
      LOGGER.debug("clearCart -> no-op, cartId={}", result.id());
      return result;
    }

    Cart cart = existing.get();
    cart.clear();
    cartItemWritePort.deleteAll(cart.id());
    cart.renewExpiry();
    cartWritePort.touch(cart.id(), cart.expiresAt());

    CartDto result = CartDtoAssembler.assemble(cart, cartPricingPort);
    LOGGER.debug("clearCart -> cartId={} emptied", result.id());
    return result;
  }
}
