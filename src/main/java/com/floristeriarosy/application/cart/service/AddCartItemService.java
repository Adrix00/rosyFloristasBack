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
import com.floristeriarosy.application.cart.support.CartOptimisticRetry;
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
   * @throws com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException the
   *     resulting quantity exceeds the 99-per-line cap (checked first: it is the binding limit
   *     whenever it is lower than real stock, so the client never has to retry against a second,
   *     different ceiling)
   * @throws CartInsufficientStockException the resulting quantity is within the per-line cap but
   *     exceeds real stock (rule 3.3)
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

    return CartOptimisticRetry.withRetry(() -> doExecute(command, productId));
  }

  /**
   * @param command the product and quantity to add, plus the caller's identity
   * @param productId {@code command.productId()}, already parsed and confirmed visible
   * @return the resulting, fully priced cart
   * @throws com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException the
   *     resulting quantity exceeds the 99-per-line cap (checked first: it is the binding limit
   *     whenever it is lower than real stock, so the client never has to retry against a second,
   *     different ceiling)
   * @throws CartInsufficientStockException the resulting quantity is within the per-line cap but
   *     exceeds real stock (rule 3.3)
   */
  private CartDto doExecute(AddCartItemCommand command, ProductId productId) {
    Optional<Cart> existing = CartFinder.find(cartReadPort, command.customerId(), command.sessionToken());
    boolean isNewCart = existing.isEmpty();
    Cart cart =
        existing.orElseGet(
            () -> Cart.createEmpty(CartId.newId(), command.customerId(), newSessionToken()));
    // Validates the 99-per-line and 50-line caps before any stock is checked or persisted; the
    // in-memory cart is simply discarded if this throws, no write has happened yet.
    cart.addOrIncrementItem(productId, command.quantity());
    requireEnoughStock(productId, cart.items().get(productId));
    cart.renewExpiry();

    // The @Version-guarded write goes first (ADR-009 amendment): if it loses the race, nothing
    // below has run yet, so CartOptimisticRetry's whole-method retry cannot double-apply the line
    // write below it.
    if (isNewCart) {
      cartWritePort.save(cart);
    } else {
      cartWritePort.touch(cart.id(), cart.expiresAt());
    }
    cartItemWritePort.save(cart.id(), productId, cart.items().get(productId));

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
