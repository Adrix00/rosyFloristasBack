package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.MergeCartCommand;
import com.floristeriarosy.application.cart.port.in.MergeCartUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link MergeCartUseCase} (cart.md, rule 3.2): folds a guest cart into the customer's
 * own cart at login, or simply reassigns it when the customer had none. Not invoked from any
 * controller in this branch (auth.md's customer login does not exist yet, feature/customer) — a
 * capability exposed for when it does.
 */
@Service
@Transactional
public class MergeCartService implements MergeCartUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(MergeCartService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;

  /**
   * @param cartReadPort resolves the guest and the customer's own cart
   * @param cartWritePort reassigns or renews the surviving cart, and deletes the discarded guest one
   * @param cartItemWritePort persists the merged lines
   */
  public MergeCartService(
      CartReadPort cartReadPort, CartWritePort cartWritePort, CartItemWritePort cartItemWritePort) {
    this.cartReadPort = cartReadPort;
    this.cartWritePort = cartWritePort;
    this.cartItemWritePort = cartItemWritePort;
  }

  /**
   * @param command the customer who just logged in, and the guest session token present at login
   */
  @Override
  public void execute(MergeCartCommand command) {
    LOGGER.debug("mergeCart customerId={}", command.customerId());

    if (command.guestSessionToken() == null || command.guestSessionToken().isBlank()) {
      LOGGER.debug("mergeCart -> no guest session token, nothing to merge");
      return;
    }
    Optional<Cart> guestCart =
        CartFinder.notExpired(cartReadPort.findBySessionToken(command.guestSessionToken()));
    if (guestCart.isEmpty()) {
      LOGGER.debug("mergeCart -> nothing to merge");
      return;
    }

    Optional<Cart> customerCart = CartFinder.notExpired(cartReadPort.findByCustomer(command.customerId()));
    if (customerCart.isEmpty()) {
      Cart cart = guestCart.get();
      cart.assignToCustomer(command.customerId());
      cart.renewExpiry();
      cartWritePort.save(cart);
      LOGGER.debug("mergeCart -> guest cart reassigned, cartId={}", cart.id());
      return;
    }

    mergeInto(customerCart.get(), guestCart.get());
  }

  /**
   * @param customerCart the surviving cart, already the customer's own
   * @param guestCart the cart to fold in and discard
   */
  private void mergeInto(Cart customerCart, Cart guestCart) {
    for (Map.Entry<ProductId, Integer> line : guestCart.items().entrySet()) {
      customerCart.mergeItem(line.getKey(), line.getValue());
    }
    for (ProductId productId : guestCart.items().keySet()) {
      cartItemWritePort.save(customerCart.id(), productId, customerCart.items().get(productId));
    }
    customerCart.renewExpiry();
    cartWritePort.touch(customerCart.id(), customerCart.expiresAt());
    cartWritePort.delete(guestCart.id());
    LOGGER.debug("mergeCart -> merged into cartId={}, guest cartId={} deleted", customerCart.id(), guestCart.id());
  }
}
