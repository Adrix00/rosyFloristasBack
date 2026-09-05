package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ClearCartService}: {@code DELETE /cart} empties, never deletes (cart.md §4, §10). */
@ExtendWith(MockitoExtension.class)
class ClearCartServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;
  @Mock private CartPricingPort cartPricingPort;

  private ClearCartService service;

  @BeforeEach
  void setUp() {
    service = new ClearCartService(cartReadPort, cartWritePort, cartItemWritePort, cartPricingPort);
  }

  @Test
  void clearingANonEmptyCartDeletesEveryLineAndReturnsTheNowEmptyCart() {
    Cart cart =
        Cart.reconstitute(
            CartId.newId(),
            null,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(3600),
            Map.of(ProductId.newId(), 2),
            null,
            null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    CartDto result = service.execute(new ClearCartCommand(UUID.randomUUID(), null));

    verify(cartItemWritePort).deleteAll(cart.id());
    assertThat(cart.items()).isEmpty();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void clearingAnAlreadyEmptyCartIsANoOpThatWritesNothing() {
    Cart cart =
        Cart.reconstitute(
            CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), Map.of(), null, null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    CartDto result = service.execute(new ClearCartCommand(UUID.randomUUID(), null));

    verify(cartItemWritePort, never()).deleteAll(any(CartId.class));
    assertThat(result.id()).isEqualTo(cart.id().value());
  }

  @Test
  void clearingWhenNoCartExistsAtAllIsANoOpThatReturnsTheVirtualEmptyCart() {
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());

    CartDto result = service.execute(new ClearCartCommand(UUID.randomUUID(), null));

    verify(cartItemWritePort, never()).deleteAll(any(CartId.class));
    assertThat(result.id()).isNull();
    assertThat(result.items()).isEmpty();
  }
}
