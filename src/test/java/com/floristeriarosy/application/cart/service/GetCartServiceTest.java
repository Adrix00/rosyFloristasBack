package com.floristeriarosy.application.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.query.GetCartQuery;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GetCartService}: read-only, never creates a cart (rule 3.1); price always live (rule 3.5). */
@ExtendWith(MockitoExtension.class)
class GetCartServiceTest {

  @Mock private CartReadPort cartReadPort;
  @Mock private CartPricingPort cartPricingPort;

  private GetCartService service;

  @BeforeEach
  void setUp() {
    service = new GetCartService(cartReadPort, cartPricingPort);
  }

  @Test
  void aFirstVisitWithNoCartYetReturnsAnEmptyVirtualCartWithoutCreatingOne() {
    when(cartReadPort.findByCustomer(any())).thenReturn(Optional.empty());

    CartDto result = service.execute(new GetCartQuery(UUID.randomUUID(), null));

    assertThat(result.id()).isNull();
    assertThat(result.items()).isEmpty();
    assertThat(result.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void thePriceReflectsWhateverThePricingPortReturnsRightNowNeverACachedValue() {
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
    when(cartPricingPort.catalogEntriesFor(any()))
        .thenReturn(List.of(entry(productId, new BigDecimal("10.00"))))
        .thenReturn(List.of(entry(productId, new BigDecimal("7.50"))));

    CartDto first = service.execute(new GetCartQuery(UUID.randomUUID(), null));
    CartDto second = service.execute(new GetCartQuery(UUID.randomUUID(), null));

    assertThat(first.items().get(0).effectivePrice()).isEqualByComparingTo("10.00");
    assertThat(second.items().get(0).effectivePrice()).isEqualByComparingTo("7.50");
    assertThat(first.subtotal()).isEqualByComparingTo("20.00");
    assertThat(second.subtotal()).isEqualByComparingTo("15.00");
  }

  private CartCatalogEntryDto entry(ProductId productId, BigDecimal price) {
    return new CartCatalogEntryDto(
        productId.value(), "Ramo", "ramo", null, price, price, false, ProductStatus.ACTIVE, null);
  }
}
