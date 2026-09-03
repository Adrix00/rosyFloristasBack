package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import java.util.List;

/** {@code GET /categories} (public): {@code ACTIVE} categories only (category.md, section 7). */
public interface GetCategoriesUseCase {

  /**
   * @return the {@code ACTIVE} categories, ordered by position then name
   */
  List<CategoryDto> execute();
}
