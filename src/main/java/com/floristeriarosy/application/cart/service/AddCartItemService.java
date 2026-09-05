package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.AddCartItemUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartDtoAssembler;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartProductNotFoundException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link AddCartItemUseCase}: adds a product, creating the cart lazily if this is its
 * first item (cart.md, rule 3.1).
 */
@Service
@Transactional
public class AddCartItemService implements AddCartItemUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(AddCartItemService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;
  private final CartPricingPort cartPricingPort;
  private final CartProductAvailabilityPort availabilityPort;

  /**
   * @param cartReadPort resolves the caller's existing cart, if any
   * @param cartWritePort persists a newly created cart row and renews its expiry
   * @param cartItemWritePort persists the touched line
   * @param cartPricingPort prices the resulting cart for the response
   * @param availabilityPort checks the product's visibility and real stock
   */
  public AddCartItemService(
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
   * @param command the product and quantity to add, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartProductNotFoundException the product does not exist or is not visible (rule 3.6)
   * @throws CartInsufficientStockException the resulting quantity exceeds real stock (rule 3.3)
   */
  @Override
  public CartDto execute(AddCartItemCommand command) {
    LOGGER.debug(
        "addCartItem hasCustomer={} productId={} quantity={}",
        command.customerId() != null,
        command.productId(),
        command.quantity());

    ProductId productId = ProductId.of(command.productId());
    if (!availabilityPort.isVisible(productId)) {
      throw new CartProductNotFoundException("Product " + productId + " is not visible");
    }

    Optional<Cart> existing = CartFinder.find(cartReadPort, command.customerId(), command.sessionToken());
    int existingQuantity = existing.map(cart -> cart.items().getOrDefault(productId, 0)).orElse(0);
    int requestedTotal = existingQuantity + command.quantity();
    requireEnoughStock(productId, requestedTotal);

    boolean isNewCart = existing.isEmpty();
    Cart cart =
        existing.orElseGet(
            () -> Cart.createEmpty(CartId.newId(), command.customerId(), newSessionToken()));
    cart.addOrIncrementItem(productId, command.quantity());

    if (isNewCart) {
      cartWritePort.save(cart);
    }
    cartItemWritePort.save(cart.id(), productId, cart.items().get(productId));
    cart.renewExpiry();
    cartWritePort.touch(cart.id(), cart.expiresAt());

    CartDto result = CartDtoAssembler.assemble(cart, cartPricingPort);
    LOGGER.debug("addCartItem -> cartId={} itemCount={}", result.id(), result.itemCount());
    return result;
  }

  /**
   * @param productId the product being added
   * @param requestedTotal the resulting quantity the line would hold
   * @throws CartInsufficientStockException {@code requestedTotal} exceeds real stock
   */
  private void requireEnoughStock(ProductId productId, int requestedTotal) {
    availabilityPort
        .availableStock(productId)
        .filter(stock -> requestedTotal > stock)
        .ifPresent(
            stock -> {
              throw new CartInsufficientStockException(
                  "Requested quantity " + requestedTotal + " exceeds available stock", stock);
            });
  }

  /**
   * @return a new, backend-generated cart session token (rule 3.1)
   */
  private String newSessionToken() {
    return UUID.randomUUID().toString();
  }
}
