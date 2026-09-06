package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.ValidateCartCommand;
import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ValidateCartService}: cart.md rule 3.4, the checkout safety net. */
@ExtendWith(MockitoExtension.class)
class ValidateCartServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartWritePort cartWritePort;
  @Mock private CartItemWritePort cartItemWritePort;
  @Mock private CartPricingPort cartPricingPort;

  private ValidateCartService service;

  @BeforeEach
  void setUp() {
    service = new ValidateCartService(cartReadPort, cartWritePort, cartItemWritePort, cartPricingPort);
  }

  private CartCatalogEntryDto entry(ProductId productId, ProductStatus status, Integer stock) {
    return new CartCatalogEntryDto(
        productId.value(), "Ramo", "ramo", null, BigDecimal.TEN, BigDecimal.TEN, false, status, stock);
  }

  @Test
  void aLineWhoseProductIsNoLongerActiveIsRemovedAutomaticallyAndReportedAsRemoved() {
    ProductId productId = ProductId.newId();
    Cart cart =
        Cart.reconstitute(
            CartId.newId(),
            null,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(3600),
            Map.of(productId, 2),
            null,
            null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of(entry(productId, ProductStatus.INACTIVE, null)));

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.valid()).isFalse();
    assertThat(result.removedItems()).extracting(removed -> removed.productId()).containsExactly(productId.value());
    assertThat(result.insufficientStockItems()).isEmpty();
    verify(cartItemWritePort).deleteAll(cart.id(), Set.of(productId));
    assertThat(cart.items()).doesNotContainKey(productId);
  }

  @Test
  void aDiscontinuedProductIsRemovedTheSameWayAnInactiveOneIs() {
    ProductId productId = ProductId.newId();
    Cart cart =
        Cart.reconstitute(
            CartId.newId(),
            null,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(3600),
            Map.of(productId, 1),
            null,
            null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of(entry(productId, ProductStatus.DISCONTINUED, null)));

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.removedItems()).hasSize(1);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void insufficientStockBlocksCheckoutWithoutAdjustingTheQuantity() {
    ProductId productId = ProductId.newId();
    Cart cart =
        Cart.reconstitute(
            CartId.newId(),
            null,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(3600),
            Map.of(productId, 5),
            null,
            null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of(entry(productId, ProductStatus.ACTIVE, 2)));

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.valid()).isFalse();
    assertThat(result.removedItems()).isEmpty();
    assertThat(result.insufficientStockItems()).hasSize(1);
    assertThat(result.insufficientStockItems().get(0).requestedQuantity()).isEqualTo(5);
    assertThat(result.insufficientStockItems().get(0).availableQuantity()).isEqualTo(2);
    assertThat(cart.items().get(productId)).isEqualTo(5);
    verify(cartItemWritePort, never()).deleteAll(any(CartId.class), any());
  }

  @Test
  void anAlreadyValidCartWritesNothing() {
    ProductId productId = ProductId.newId();
    Cart cart =
        Cart.reconstitute(
            CartId.newId(),
            null,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(3600),
            Map.of(productId, 2),
            null,
            null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(cartPricingPort.catalogEntriesFor(any())).thenReturn(List.of(entry(productId, ProductStatus.ACTIVE, 10)));

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.valid()).isTrue();
    verify(cartItemWritePort, never()).deleteAll(any(CartId.class), any());
    verify(cartWritePort, never()).touch(any(CartId.class), any());
  }

  @Test
  void removedItemsAloneStillLetsCheckoutContinueWhileInsufficientStockBlocksIt() {
    ProductId removedProduct = ProductId.newId();
    ProductId shortStockProduct = ProductId.newId();
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    items.put(removedProduct, 1);
    items.put(shortStockProduct, 5);
    Cart cart =
        Cart.reconstitute(
            CartId.newId(), null, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), items, null, null);
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.of(cart));
    when(cartPricingPort.catalogEntriesFor(any()))
        .thenReturn(
            List.of(entry(removedProduct, ProductStatus.INACTIVE, null), entry(shortStockProduct, ProductStatus.ACTIVE, 1)));

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.valid()).isFalse();
    assertThat(result.removedItems()).isNotEmpty();
    assertThat(result.insufficientStockItems()).isNotEmpty();
  }

  @Test
  void validatingAnEmptyOrNonexistentCartIsTriviallyValid() {
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());

    CartValidationDto result = service.execute(new ValidateCartCommand(UUID.randomUUID(), null));

    assertThat(result.valid()).isTrue();
    assertThat(result.removedItems()).isEmpty();
    assertThat(result.insufficientStockItems()).isEmpty();
  }
}
