package com.floristeriarosy.application.cart.port.out;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import java.util.Set;

/**
 * Batch pricing/display capability for cart (ADR-003; cart.md §8): {@code cart} never reimplements
 * {@code effectivePrice} — it asks {@code product} for it, by product id, in one query per cart
 * rather than one per line.
 */
public interface CartPricingPort {

  /**
   * Everything a cart line's response needs about its product — name, slug, image, price,
   * {@code onSale}, status and available stock — in one query, joined against {@code products} and
   * {@code product_discounts}. Backs {@code GET /cart} and every write use case's response.
   *
   * @param productIds the products to look up
   * @return one entry per found product; a product referenced by a cart line always exists
   *     ({@code cart_items.product_id} is {@code ON DELETE CASCADE})
   */
  List<CartCatalogEntryDto> catalogEntriesFor(Set<ProductId> productIds);
}
