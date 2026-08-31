package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;

/**
 * Category's only outbound dependency on products, read-only (category.md, section 8): the impact
 * preview before deactivating or deleting a category.
 */
public interface CategoryProductsPort {

  /**
   * @param id the category to count products for
   * @return number of products associated with it, regardless of status
   */
  long countByCategory(CategoryId id);

  /**
   * @param id the category being previewed for deactivation
   * @return {@code ACTIVE} products for which {@code id} is their only {@code ACTIVE} category
   */
  List<CategoryProductRef> findLosingVisibility(CategoryId id);

  /**
   * @param id the category being previewed for deletion
   * @return products that would be left with zero categories if {@code id} is deleted
   */
  List<CategoryProductRef> findLeftWithoutCategory(CategoryId id);
}
