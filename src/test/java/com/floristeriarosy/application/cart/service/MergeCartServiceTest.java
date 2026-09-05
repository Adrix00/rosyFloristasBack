package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.MergeCartCommand;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
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

/**
 * {@link MergeCartService}: cart.md rule 3.2, called directly — no controller exists yet for it in
 * this branch (customer login is feature/customer).
 */
@ExtendWith(MockitoExtension.class)
class MergeCartServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;

  private MergeCartService service;

  @BeforeEach
  void setUp() {
    service = new MergeCartService(cartReadPort, cartWritePort, cartItemWritePort);
  }

  private Cart cartWith(UUID customerId, Map<ProductId, Integer> items) {
    return Cart.reconstitute(
        CartId.newId(), customerId, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
  }

  @Test
  void mergingSumsQuantitiesPresentInBothCartsCappedAtTheLinePerLineMaximum() {
    ProductId shared = ProductId.newId();
    UUID customerId = UUID.randomUUID();
    Cart customerCart = cartWith(customerId, mapOf(shared, 60));
    Cart guestCart = cartWith(null, mapOf(shared, 60));
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.of(guestCart));
    when(cartReadPort.findByCustomer(customerId)).thenReturn(Optional.of(customerCart));

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    assertThat(customerCart.items().get(shared)).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
    verify(cartItemWritePort).save(customerCart.id(), shared, Cart.MAX_QUANTITY_PER_LINE);
  }

  @Test
  void aProductPresentOnlyInOneOfTheTwoCartsIsCopiedAsIs() {
    ProductId onlyInGuestCart = ProductId.newId();
    UUID customerId = UUID.randomUUID();
    Cart customerCart = cartWith(customerId, mapOf(ProductId.newId(), 1));
    Cart guestCart = cartWith(null, mapOf(onlyInGuestCart, 3));
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.of(guestCart));
    when(cartReadPort.findByCustomer(customerId)).thenReturn(Optional.of(customerCart));

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    assertThat(customerCart.items()).containsEntry(onlyInGuestCart, 3);
  }

  @Test
  void findingTheSameCartAsBothGuestAndCustomerNeitherDoublesNorDeletesIt() {
    // The same session_token cookie can resolve as both the guest cart and the customer's own
    // (e.g. a second login without clearing cookies). Merging a cart into itself would double
    // every line via mergeInto and then delete what it just doubled.
    UUID customerId = UUID.randomUUID();
    ProductId productId = ProductId.newId();
    Cart sameCart = cartWith(customerId, mapOf(productId, 3));
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.of(sameCart));
    when(cartReadPort.findByCustomer(customerId)).thenReturn(Optional.of(sameCart));

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    assertThat(sameCart.items()).containsEntry(productId, 3);
    verify(cartWritePort, never()).delete(any(CartId.class));
    verify(cartItemWritePort, never()).save(any(), any(), anyInt());
  }

  @Test
  void theGuestCartIsDeletedOnceItsLinesAreFoldedIntoTheCustomersOwn() {
    UUID customerId = UUID.randomUUID();
    Cart customerCart = cartWith(customerId, mapOf(ProductId.newId(), 1));
    Cart guestCart = cartWith(null, mapOf(ProductId.newId(), 1));
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.of(guestCart));
    when(cartReadPort.findByCustomer(customerId)).thenReturn(Optional.of(customerCart));

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    verify(cartWritePort).delete(guestCart.id());
  }

  @Test
  void aCustomerWithNoPriorCartOfTheirOwnSimplyInheritsTheGuestCartWithoutMerging() {
    UUID customerId = UUID.randomUUID();
    Cart guestCart = cartWith(null, mapOf(ProductId.newId(), 2));
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.of(guestCart));
    when(cartReadPort.findByCustomer(customerId)).thenReturn(Optional.empty());
    when(cartWritePort.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    assertThat(guestCart.customerId()).isEqualTo(customerId);
    verify(cartWritePort).save(guestCart);
    verify(cartWritePort, never()).delete(any(CartId.class));
  }

  @Test
  void aBlankGuestSessionTokenHasNothingToMerge() {
    service.execute(new MergeCartCommand(UUID.randomUUID(), ""));

    verify(cartReadPort, never()).findBySessionToken(any());
    verify(cartWritePort, never()).save(any());
  }

  @Test
  void aGuestSessionTokenWithNoMatchingCartHasNothingToMerge() {
    UUID customerId = UUID.randomUUID();
    when(cartReadPort.findBySessionToken("guest-token")).thenReturn(Optional.empty());

    service.execute(new MergeCartCommand(customerId, "guest-token"));

    verify(cartReadPort, never()).findByCustomer(any());
    verify(cartWritePort, never()).save(any());
  }

  private Map<ProductId, Integer> mapOf(ProductId productId, int quantity) {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(productId, quantity);
    return items;
  }
}
