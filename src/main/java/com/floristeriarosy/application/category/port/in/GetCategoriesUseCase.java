package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import java.util.List;

/** The public and the admin category listings (category.md, section 7). */
public interface GetCategoriesUseCase {

  /**
   * @param query {@code includeInactive=false} for the public listing, {@code true} for admin
   * @return the matching categories, ordered by position then name
   */
  List<CategoryDto> execute(GetCategoriesQuery query);
}
