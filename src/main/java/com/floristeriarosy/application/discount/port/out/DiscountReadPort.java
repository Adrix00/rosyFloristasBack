package com.floristeriarosy.application.discount.port.out;

import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import java.util.Optional;

/** Retrieves discounts (ADR-003; product-discounts.md, section 8). */
public interface DiscountReadPort {

  /**
   * @param id the discount to load
   * @return the discount, if it exists
   */
  Optional<Discount> findById(DiscountId id);

  /**
   * @param productId the product whose discount history to list
   * @return every discount ever created for the product, most recent first
   */
  List<Discount> findByProduct(ProductId productId);

  /**
   * @param productId the product to check
   * @return the discount whose vigency window contains the current instant, if any
   *     (product-discounts.md, section 3.1); it may still be {@code SOLD_OUT}, callers wanting an
   *     applicable price must check {@link Discount#state()} themselves
   */
  Optional<Discount> findActiveForProduct(ProductId productId);
}
