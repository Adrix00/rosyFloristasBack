package com.floristeriarosy.application.discount.port.out;

import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;

/** Persists changes to a discount (ADR-003; product-discounts.md, section 8). */
public interface DiscountWritePort {

  /**
   * @param discount the discount to insert or update
   * @return the saved discount, with timestamps populated by the database
   * @throws DiscountOverlapException the vigency window overlaps another discount of the same
   *     product ({@code ex_product_discounts_no_overlap})
   */
  Discount save(Discount discount);

  /**
   * @param id the discount to delete
   */
  void delete(DiscountId id);

  /**
   * Sets {@code ends_at = now()} at the database, closing the discount (product-discounts.md,
   * section 3.4). The caller is expected to have already validated the discount can be ended
   * (e.g. via {@link Discount#end()}).
   *
   * @param id the discount to close now
   * @return the closed discount
   */
  Discount endNow(DiscountId id);
}
