package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;

/** Preview before deactivating or deleting a category (category.md, section 7). */
public interface GetCategoryImpactUseCase {

  /**
   * @param query id of the category to preview
   * @return total associated products, and the two impact lists
   */
  CategoryImpact execute(GetCategoryImpactQuery query);
}
