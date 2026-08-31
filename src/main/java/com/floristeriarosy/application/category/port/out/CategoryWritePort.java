package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;

public interface CategoryWritePort {

  Category save(Category category);

  void delete(CategoryId id);

  /**
   * Sets {@code position} to each id's index in the list, in one transaction (category.md §3.5).
   */
  void updatePositions(List<CategoryId> orderedIds);
}
