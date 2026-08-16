package com.floristeriarosy.infrastructure.persistence.adapter.category;

import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryPersistenceAdapter
    implements CategoryReadPort, CategoryWritePort, CategoryExistencePort {

  @Override
  public Optional<Category> findById(CategoryId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<Category> findAll() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Category save(Category category) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void delete(CategoryId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean existsById(CategoryId id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
