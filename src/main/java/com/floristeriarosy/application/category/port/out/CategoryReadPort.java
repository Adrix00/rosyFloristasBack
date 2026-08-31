package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import java.util.Optional;

public interface CategoryReadPort {

  Optional<Category> findById(CategoryId id);

  Optional<Category> findBySlug(String slug);

  /** {@code ACTIVE} only, ordered by {@code position} then {@code name} (category.md, §4). */
  List<Category> findAllActive();

  /** All statuses, same order. Backs {@code GET /categories/all} (ADMIN). */
  List<Category> findAll();
}
