package com.floristeriarosy.application.category.port.out;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import java.util.Optional;

public interface CategoryReadPort {

  Optional<Category> findById(CategoryId id);

  List<Category> findAll();
}
