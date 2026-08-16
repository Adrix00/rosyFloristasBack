package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.valueobject.CategoryId;

public interface CategoryExistencePort {

  boolean existsById(CategoryId id);
}
