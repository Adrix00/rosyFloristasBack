package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.query.GetCategoryQuery;

/** Public lookup of one category, by UUID or by slug (category.md, section 7). */
public interface GetCategoryUseCase {

  /**
   * @param query a UUID or a slug
   * @return the matching, {@code ACTIVE} category
   */
  CategoryDto execute(GetCategoryQuery query);
}
