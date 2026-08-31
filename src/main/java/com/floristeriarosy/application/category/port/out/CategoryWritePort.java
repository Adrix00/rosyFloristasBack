package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;

/** Write capability for category (ADR-003). */
public interface CategoryWritePort {

  /**
   * @param category the category to insert or update
   * @return the saved category, with timestamps populated by the database
   */
  Category save(Category category);

  /**
   * @param id the category to delete; {@code product_categories} rows cascade
   */
  void delete(CategoryId id);

  /**
   * Sets {@code position} to each id's index in the list, in one transaction (category.md §3.5).
   *
   * @param orderedIds every category id, in its new order
   */
  void updatePositions(List<CategoryId> orderedIds);
}
