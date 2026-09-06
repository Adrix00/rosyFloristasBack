package com.floristeriarosy.domain.model.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.exception.cart.CartLineLimitExceededException;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link Cart} invariants (cart.md, rules 3.1 to 3.3). */
class CartTest {

  private Cart emptyCart() {
    return Cart.createEmpty(CartId.newId(), null, UUID.randomUUID().toString());
  }

  private Cart cartWithLines(int lineCount) {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    for (int i = 0; i < lineCount; i++) {
      items.put(ProductId.newId(), 1);
    }
    return Cart.reconstitute(
        CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
  }

  @Test
  void addingAProductAlreadyPresentSumsOntoTheExistingQuantityInsteadOfReplacingIt() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();

    cart.addOrIncrementItem(productId, 3);
    cart.addOrIncrementItem(productId, 2);

    assertThat(cart.items()).containsEntry(productId, 5);
  }

  @Test
  void addingAResultingQuantityAboveNinetyNineThrows() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 90);

    assertThatThrownBy(() -> cart.addOrIncrementItem(productId, 10))
        .isInstanceOf(CartItemLimitExceededException.class);
  }

  @Test
  void addingA51stDistinctLineThrowsTheLineLimit() {
    Cart cart = cartWithLines(Cart.MAX_LINES);

    assertThatThrownBy(() -> cart.addOrIncrementItem(ProductId.newId(), 1))
        .isInstanceOf(CartLineLimitExceededException.class);
  }

  @Test
  void incrementingAnExistingLineIsAllowedEvenWhenTheCartAlreadyHasTheMaximumLineCount() {
    Cart cart = cartWithLines(Cart.MAX_LINES);
    ProductId existingProduct = cart.items().keySet().iterator().next();

    cart.addOrIncrementItem(existingProduct, 1);

    assertThat(cart.items()).hasSize(Cart.MAX_LINES);
    assertThat(cart.items().get(existingProduct)).isEqualTo(2);
  }

  @Test
  void setItemQuantityFixesTheValueInsteadOfAddingToIt() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 5);

    cart.setItemQuantity(productId, 2);

    assertThat(cart.items()).containsEntry(productId, 2);
  }

  @Test
  void setItemQuantityOnAProductNotInTheCartThrowsItemNotFound() {
    Cart cart = emptyCart();

    assertThatThrownBy(() -> cart.setItemQuantity(ProductId.newId(), 1))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void setItemQuantityAboveNinetyNineThrows() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 1);

    assertThatThrownBy(() -> cart.setItemQuantity(productId, 100))
        .isInstanceOf(CartItemLimitExceededException.class);
  }

  @Test
  void removeItemOnAProductNotInTheCartThrowsItemNotFound() {
    Cart cart = emptyCart();

    assertThatThrownBy(() -> cart.removeItem(ProductId.newId()))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void clearingAnAlreadyEmptyCartIsANoOpNotAnError() {
    Cart cart = emptyCart();

    cart.clear();

    assertThat(cart.items()).isEmpty();
  }

  @Test
  void removeItemsDeletesOnlyTheGivenSubset() {
    Cart cart = emptyCart();
    ProductId keep = ProductId.newId();
    ProductId drop = ProductId.newId();
    cart.addOrIncrementItem(keep, 1);
    cart.addOrIncrementItem(drop, 1);

    cart.removeItems(Set.of(drop));

    assertThat(cart.items()).containsOnlyKeys(keep);
  }

  @Test
  void mergeItemCapsTheSummedQuantitySilentlyInsteadOfRejectingIt() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 60);

    cart.mergeItem(productId, 60);

    assertThat(cart.items().get(productId)).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
  }

  @Test
  void mergeItemSumsWhenTheCombinedQuantityStaysUnderTheCap() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 10);

    cart.mergeItem(productId, 5);

    assertThat(cart.items().get(productId)).isEqualTo(15);
  }

  @Test
  void assignToCustomerReassignsAGuestCartWithoutTouchingItsLines() {
    Cart cart = emptyCart();
    ProductId productId = ProductId.newId();
    cart.addOrIncrementItem(productId, 1);
    UUID customerId = UUID.randomUUID();

    cart.assignToCustomer(customerId);

    assertThat(cart.customerId()).isEqualTo(customerId);
    assertThat(cart.items()).containsKey(productId);
  }

  @Test
  void aCartPastItsExpiryIsExpired() {
    Cart cart =
        Cart.reconstitute(
            CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().minusSeconds(1), Map.of(), null, null);

    assertThat(cart.isExpired()).isTrue();
  }

  @Test
  void aCartBeforeItsExpiryIsNotExpired() {
    Cart cart = emptyCart();

    assertThat(cart.isExpired()).isFalse();
  }
}
