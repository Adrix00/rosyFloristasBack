package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.query.GetCartQuery;
import com.floristeriarosy.application.cart.support.CartDtoAssembler;
import com.floristeriarosy.application.cart.support.CartFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetCartUseCase}: read-only, never creates a cart (cart.md, rule 3.1). Not
 * {@code @Transactional} (07-transaction-conventions.md: reading use cases must not open one).
 */
@Service
public class GetCartService implements GetCartUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCartService.class);

  private final CartReadPort cartReadPort;
  private final CartPricingPort cartPricingPort;

  /**
   * @param cartReadPort resolves the caller's existing cart, if any
   * @param cartPricingPort prices the cart for the response
   */
  public GetCartService(CartReadPort cartReadPort, CartPricingPort cartPricingPort) {
    this.cartReadPort = cartReadPort;
    this.cartPricingPort = cartPricingPort;
  }

  /**
   * @param query the caller's identity
   * @return the fully priced cart, or the empty virtual cart if none has ever been created
   */
  @Override
  public CartDto execute(GetCartQuery query) {
    LOGGER.debug("getCart hasCustomer={}", query.customerId() != null);
    CartDto result =
        CartFinder.find(cartReadPort, query.customerId(), query.sessionToken())
            .map(cart -> CartDtoAssembler.assemble(cart, cartPricingPort))
            .orElseGet(CartDto::empty);
    LOGGER.debug("getCart -> cartId={} itemCount={}", result.id(), result.itemCount());
    return result;
  }
}
