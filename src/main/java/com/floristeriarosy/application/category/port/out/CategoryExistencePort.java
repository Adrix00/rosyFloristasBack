package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.valueobject.CategoryId;

/** Existence checks for category (ADR-003). */
public interface CategoryExistencePort {

  /**
   * @param id the category to check
   * @return whether it exists
   */
  boolean existsById(CategoryId id);

  /**
   * @param slug the slug to check
   * @return whether a category already uses it
   */
  boolean existsBySlug(String slug);
}
