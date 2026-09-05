package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.RemoveCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link RemoveCartItemService}: cart.md §4, §7, and the {@code CART_ITEM_NOT_FOUND} case borde. */
@ExtendWith(MockitoExtension.class)
class RemoveCartItemServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;
  @Mock private CartPricingPort cartPricingPort;

  private RemoveCartItemService service;

  @BeforeEach
  void setUp() {
    service = new RemoveCartItemService(cartReadPort, cartWritePort, cartItemWritePort, cartPricingPort);
  }

  private Cart cartWith(ProductId productId, int quantity) {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(productId, quantity);
    return Cart.reconstitute(
        CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
  }

  @Test
  void removingAnExistingLineReturnsTheResultingCartAndDeletesTheLine() {
    ProductId productId = ProductId.newId();
    Cart cart = cartWith(productId, 3);

    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    CartDto result =
        service.execute(new RemoveCartItemCommand(UUID.randomUUID(), null, productId.value()));

    verify(cartItemWritePort).delete(cart.id(), productId);
    assertThat(cart.items()).doesNotContainKey(productId);
    assertThat(result.id()).isEqualTo(cart.id().value());
  }

  @Test
  void removingFromAProductNotInAnExistingCartThrowsItemNotFound() {
    Cart cart = cartWith(ProductId.newId(), 1);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    assertThatThrownBy(
            () ->
                service.execute(
                    new RemoveCartItemCommand(UUID.randomUUID(), null, UUID.randomUUID())))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void removingWhenNoCartExistsAtAllThrowsItemNotFound() {
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new RemoveCartItemCommand(UUID.randomUUID(), null, UUID.randomUUID())))
        .isInstanceOf(CartItemNotFoundException.class);
  }
}
