package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;

/**
 * Category's only outbound dependency on products, read-only (category.md, section 8): the impact
 * preview before deactivating or deleting a category.
 */
public interface CategoryProductsPort {

  long countByCategory(CategoryId id);

  /** {@code ACTIVE} products for which this category is their only {@code ACTIVE} category. */
  List<CategoryProductRef> findLosingVisibility(CategoryId id);

  /** Products that would be left with zero categories if this one is deleted. */
  List<CategoryProductRef> findLeftWithoutCategory(CategoryId id);
}
