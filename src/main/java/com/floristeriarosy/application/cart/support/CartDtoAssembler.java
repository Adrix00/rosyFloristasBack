package com.floristeriarosy.application.cart.support;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the priced {@link CartDto} every use case in this module returns, from the domain {@link
 * Cart} and one batch call to {@link CartPricingPort} (cart.md §6, §8: {@code GET /cart} and every
 * write use case's response share this assembly).
 */
public final class CartDtoAssembler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartDtoAssembler.class);

  private CartDtoAssembler() {}

  /**
   * @param cart the cart to price
   * @param pricingPort resolves the display data for every line in one query
   * @return the fully priced cart
   */
  public static CartDto assemble(Cart cart, CartPricingPort pricingPort) {
    LOGGER.debug("assemble cartId={} lines={}", cart.id(), cart.items().size());
    if (cart.items().isEmpty()) {
      CartDto result =
          new CartDto(cart.id().value(), cart.customerId(), cart.sessionToken(), List.of(), BigDecimal.ZERO, 0);
      LOGGER.debug("assemble cartId={} -> itemCount=0", cart.id());
      return result;
    }

    Map<UUID, CartCatalogEntryDto> catalog =
        pricingPort.catalogEntriesFor(cart.items().keySet()).stream()
            .collect(Collectors.toMap(CartCatalogEntryDto::productId, entry -> entry));

    List<CartItemDto> items = new ArrayList<>();
    for (Map.Entry<ProductId, Integer> line : cart.items().entrySet()) {
      CartCatalogEntryDto entry = catalog.get(line.getKey().value());
      if (entry == null) {
        // Defensive only: cart_items.product_id is ON DELETE CASCADE, so a line always has a
        // matching product row. Skipping rather than failing the whole cart on an inconsistency.
        LOGGER.debug("assemble cartId={} productId={} -> no catalog entry, skipped", cart.id(), line.getKey());
        continue;
      }
      items.add(toItemDto(line.getValue(), entry));
    }

    BigDecimal subtotal = items.stream().map(CartItemDto::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    int itemCount = items.stream().mapToInt(CartItemDto::quantity).sum();
    CartDto result =
        new CartDto(cart.id().value(), cart.customerId(), cart.sessionToken(), items, subtotal, itemCount);
    LOGGER.debug("assemble cartId={} -> itemCount={} subtotal={}", cart.id(), itemCount, subtotal);
    return result;
  }

  /**
   * @param quantity the line's quantity
   * @param entry the product's display data
   * @return the priced line
   */
  private static CartItemDto toItemDto(int quantity, CartCatalogEntryDto entry) {
    BigDecimal lineTotal = entry.effectivePrice().multiply(BigDecimal.valueOf(quantity));
    return new CartItemDto(
        entry.productId(),
        entry.productName(),
        entry.productSlug(),
        entry.mainImageUrl(),
        entry.unitPrice(),
        entry.effectivePrice(),
        entry.onSale(),
        quantity,
        lineTotal,
        entry.availableQuantity());
  }
}
