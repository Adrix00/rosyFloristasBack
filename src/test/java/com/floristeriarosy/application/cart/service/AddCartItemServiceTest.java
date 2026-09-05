package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
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
    return existingCartWithId(CartId.newId(), items);
  }

  private Cart existingCartWithId(CartId cartId, Map<ProductId, Integer> items) {
    return Cart.reconstitute(
        cartId, null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
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
  void thePerLineCapIsCheckedBeforeStockWhenBothWouldReject() {
    // Line already at 90, stock 100: adding 20 would reach 110, which both exceeds the 99-per-
    // line cap AND the 100 in stock. The cap is the binding limit and must be the error reported
    // — never CART_INSUFFICIENT_STOCK with availableQuantity=100, which the client could retry
    // with and still fail, this time against a different code.
    ProductId productId = ProductId.newId();
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(productId, 90);
    Cart cart = existingCart(items);
    when(availabilityPort.isVisible(productId)).thenReturn(true);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));

    assertThatThrownBy(
            () -> service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId.value(), 20)))
        .isInstanceOf(CartItemLimitExceededException.class);
    verify(availabilityPort, never()).availableStock(any());
    verify(cartItemWritePort, never()).save(any(), any(), anyInt());
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

    assertThatThrownBy(
            () ->
                service.execute(
                    new AddCartItemCommand(UUID.randomUUID(), null, newProductId.value(), 1)))
        .isInstanceOf(CartLineLimitExceededException.class);
    verify(cartItemWritePort, never()).save(any(), any(), anyInt());
  }

  @Test
  void retriesOnceAndSucceedsWhenTheFirstWriteLosesTheVersionRace() {
    // ADR-009 amendment (2026-09-06): a version conflict on the cart's own row is retried once,
    // transparently, by re-resolving the cart and reapplying the same add — never surfaced to the
    // client as 409.
    ProductId productId = ProductId.newId();
    CartId cartId = existingCart(Map.of(productId, 1)).id();
    // A fresh Cart per read: the real adapter would return the true persisted quantity (1) on the
    // retry too, since the version-guarded touch below fails before any line write is attempted —
    // nothing about the first, failed attempt is visible to re-read.
    when(availabilityPort.isVisible(productId)).thenReturn(true);
    when(cartReadPort.findByCustomer(any()))
        .thenAnswer(invocation -> Optional.of(existingCartWithId(cartId, Map.of(productId, 1))));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of());
    doThrow(new ResourceModifiedException("lost the race"))
        .doNothing()
        .when(cartWritePort)
        .touch(any(), any());

    CartDto result = service.execute(new AddCartItemCommand(UUID.randomUUID(), null, productId.value(), 2));

    assertThat(result.id()).isNotNull();
    verify(cartReadPort, times(2)).findByCustomer(any());
    verify(cartWritePort, times(2)).touch(any(), any());
    // The version-guarded touch runs before the line write (ADR-009 amendment): the first attempt
    // never reaches cartItemWritePort.save, so the retry applies "add 2" exactly once, not twice.
    verify(cartItemWritePort, times(1)).save(cartId, productId, 3);
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
