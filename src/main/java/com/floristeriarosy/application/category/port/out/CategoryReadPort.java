package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import java.util.Optional;

/** Read capability for category (ADR-003). */
public interface CategoryReadPort {

  /**
   * @param id the category to load
   * @return the category, if it exists
   */
  Optional<Category> findById(CategoryId id);

  /**
   * @param slug the category to load
   * @return the category, if it exists
   */
  Optional<Category> findBySlug(String slug);

  /**
   * @return {@code ACTIVE} categories, ordered by position then name (category.md, §4)
   */
  List<Category> findAllActive();

  /**
   * @return every category regardless of status. Backs {@code GET /categories/all} (ADMIN)
   */
  List<Category> findAll();
}
