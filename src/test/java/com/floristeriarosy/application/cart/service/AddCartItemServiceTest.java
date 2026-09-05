package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartLineLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartProductNotFoundException;
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

/** {@link AddCartItemService}: cart.md rules 3.1, 3.3, 3.6. */
@ExtendWith(MockitoExtension.class)
class AddCartItemServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;
  @Mock private CartPricingPort cartPricingPort;
  @Mock private CartProductAvailabilityPort availabilityPort;

  private AddCartItemService service;

  @BeforeEach
  void setUp() {
    service = new AddCartItemService(cartReadPort, cartWritePort, cartItemWritePort, cartPricingPort, availabilityPort);
  }

  private Cart existingCart(Map<ProductId, Integer> items) {
    return Cart.reconstitute(
        CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
  }

  @Test
  void firstAddCreatesTheCartLazilyBecauseNoneExistedYet() {
    UUID productId = UUID.randomUUID();
    when(availabilityPort.isVisible(any(ProductId.class))).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());
    when(availabilityPort.availableStock(any(ProductId.class))).thenReturn(Optional.empty());
    when(cartWritePort.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    CartDto result = service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId, 2));

    verify(cartWritePort).save(any(Cart.class));
    verify(cartItemWritePort).save(any(CartId.class), eq(ProductId.of(productId)), eq(2));
    assertThat(result.id()).isNotNull();
  }

  @Test
  void addingToAnExistingCartSumsOntoTheCurrentQuantityInsteadOfCreatingANewCart() {
    ProductId productId = ProductId.newId();
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(productId, 3);
    Cart cart = existingCart(items);
    when(availabilityPort.isVisible(productId)).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(availabilityPort.availableStock(productId)).thenReturn(Optional.empty());
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId.value(), 2));

    verify(cartWritePort, never()).save(any(Cart.class));
    verify(cartItemWritePort).save(cart.id(), productId, 5);
  }

  @Test
  void addingAProductThatIsNotVisibleIsRejectedAsNotFoundBeforeTouchingTheCart() {
    UUID productId = UUID.randomUUID();
    when(availabilityPort.isVisible(any(ProductId.class))).thenReturn(false);

    assertThatThrownBy(
            () -> service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId, 1)))
        .isInstanceOf(CartProductNotFoundException.class);
    verify(cartWritePort, never()).save(any());
    verify(cartItemWritePort, never()).save(any(), any(), anyInt());
  }

  @Test
  void requestingMoreThanTheRealAvailableStockIsRejectedWithTheRealAvailableQuantity() {
    UUID productId = UUID.randomUUID();
    when(availabilityPort.isVisible(any(ProductId.class))).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());
    when(availabilityPort.availableStock(any(ProductId.class))).thenReturn(Optional.of(3));

    assertThatThrownBy(
            () -> service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId, 5)))
        .isInstanceOf(CartInsufficientStockException.class)
        .satisfies(
            exception ->
                assertThat(((CartInsufficientStockException) exception).errorDetails())
                    .containsEntry("availableQuantity", 3));
  }

  @Test
  void aProductWithNoManagedInventoryHasNoStockLimitToCheck() {
    UUID productId = UUID.randomUUID();
    when(availabilityPort.isVisible(any(ProductId.class))).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());
    when(availabilityPort.availableStock(any(ProductId.class))).thenReturn(Optional.empty());
    when(cartWritePort.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    CartDto result = service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId, 99));

    assertThat(result.id()).isNotNull();
  }

  @Test
  void addingA51stDistinctLineIsRejectedByTheLineLimit() {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    for (int i = 0; i < Cart.MAX_LINES; i++) {
      items.put(ProductId.newId(), 1);
    }
    Cart cart = existingCart(items);
    ProductId newProductId = ProductId.newId();
    when(availabilityPort.isVisible(newProductId)).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(availabilityPort.availableStock(newProductId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new AddCartItemCommand(UUID.randomUUID(), null, newProductId.value(), 1)))
        .isInstanceOf(CartLineLimitExceededException.class);
    verify(cartItemWritePort, never()).save(any(), any(), anyInt());
  }

  @Test
  void anExistingCartFindsItsOwnerBySessionTokenWhenAnonymous() {
    UUID productId = UUID.randomUUID();
    String sessionToken = "guest-token";
    when(availabilityPort.isVisible(any(ProductId.class))).thenReturn(true);
    when(cartReadPort.findBySessionToken(sessionToken)).thenReturn(Optional.empty());
    when(availabilityPort.availableStock(any(ProductId.class))).thenReturn(Optional.empty());
    when(cartWritePort.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());

    service.execute(new AddCartItemCommand(null, sessionToken, productId, 1));

    verify(cartReadPort).findBySessionToken(sessionToken);
    verify(cartReadPort, never()).findByCustomer(any());
  }
}
