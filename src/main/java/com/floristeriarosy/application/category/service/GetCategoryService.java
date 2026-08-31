package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.GetCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.query.GetCategoryQuery;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.CategoryStatus;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetCategoryService implements GetCategoryUseCase {

  private final CategoryReadPort readPort;

  public GetCategoryService(CategoryReadPort readPort) {
    this.readPort = readPort;
  }

  @Override
  public CategoryDto execute(GetCategoryQuery query) {
    Category category =
        lookUp(query.idOrSlug())
            .filter(found -> found.status() == CategoryStatus.ACTIVE)
            .orElseThrow(
                () -> new CategoryNotFoundException("Category " + query.idOrSlug() + " not found"));
    return CategoryDtoMapper.toDto(category);
  }

  private Optional<Category> lookUp(String idOrSlug) {
    try {
      return readPort.findById(CategoryId.of(UUID.fromString(idOrSlug)));
    } catch (IllegalArgumentException notAUuid) {
      return readPort.findBySlug(idOrSlug);
    }
  }
}
