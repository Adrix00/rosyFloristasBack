package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.UpdateCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link UpdateCartItemService}: cart.md §5 ({@code PATCH} fixes, never sums) and rule 3.3. */
@ExtendWith(MockitoExtension.class)
class UpdateCartItemServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;
  @Mock private CartPricingPort cartPricingPort;
  @Mock private CartProductAvailabilityPort availabilityPort;

  private UpdateCartItemService service;

  @BeforeEach
  void setUp() {
    service =
        new UpdateCartItemService(cartReadPort, cartWritePort, cartItemWritePort, cartPricingPort, availabilityPort);
  }

  private Cart cartWith(ProductId productId, int quantity) {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(productId, quantity);
    return Cart.reconstitute(
        CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
  }

  @Test
  void patchFixesTheQuantityInsteadOfAddingToTheCurrentOne() {
    ProductId productId = ProductId.newId();
    Cart cart = cartWith(productId, 3);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(availabilityPort.availableStock(productId)).thenReturn(Optional.empty());
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    UUID customerId = UUID.randomUUID();
    service.execute(new UpdateCartItemCommand(customerId, null, productId.value(), 10));

    verify(cartItemWritePort).save(cart.id(), productId, 10);
    assertThat(cart.items().get(productId)).isEqualTo(10);
  }

  @Test
  void updatingAProductNotInTheCartThrowsItemNotFound() {
    Cart cart = cartWith(ProductId.newId(), 1);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateCartItemCommand(UUID.randomUUID(), null, UUID.randomUUID(), 5)))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void updatingWhenNoCartExistsAtAllThrowsItemNotFound() {
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateCartItemCommand(UUID.randomUUID(), null, UUID.randomUUID(), 5)))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void settingAQuantityAboveRealStockIsRejectedWithTheRealAvailableQuantity() {
    ProductId productId = ProductId.newId();
    Cart cart = cartWith(productId, 2);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(availabilityPort.availableStock(productId)).thenReturn(Optional.of(4));

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateCartItemCommand(UUID.randomUUID(), null, productId.value(), 6)))
        .isInstanceOf(CartInsufficientStockException.class)
        .satisfies(
            exception ->
                assertThat(((CartInsufficientStockException) exception).errorDetails())
                    .containsEntry("availableQuantity", 4));
  }

  @Test
  void settingTheSameQuantityAlreadyThereIsNotAnErrorAndSimplyLeavesItUnchanged() {
    ProductId productId = ProductId.newId();
    Cart cart = cartWith(productId, 4);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(availabilityPort.availableStock(productId)).thenReturn(Optional.empty());
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    CartDto result =
        service.execute(new UpdateCartItemCommand(UUID.randomUUID(), null, productId.value(), 4));

    assertThat(result.id()).isEqualTo(cart.id().value());
    assertThat(cart.items().get(productId)).isEqualTo(4);
  }
}
