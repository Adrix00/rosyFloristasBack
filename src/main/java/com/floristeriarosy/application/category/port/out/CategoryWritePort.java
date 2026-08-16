package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;

public interface CategoryWritePort {

  Category save(Category category);

  void delete(CategoryId id);
}
