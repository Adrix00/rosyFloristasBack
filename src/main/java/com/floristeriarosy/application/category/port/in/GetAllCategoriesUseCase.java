package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.dto.CategoryDto;
import java.util.List;

/** {@code GET /categories/all} (ADMIN): every status (category.md, section 7). */
public interface GetAllCategoriesUseCase {

  /**
   * @return every category regardless of status, ordered by position then name
   */
  List<CategoryDto> execute();
}
