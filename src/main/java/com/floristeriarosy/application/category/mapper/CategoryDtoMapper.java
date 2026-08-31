package com.floristeriarosy.application.category.mapper;

import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.domain.model.category.Category;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class CategoryDtoMapper {

  private CategoryDtoMapper() {}

  /**
   * @param category the domain category to expose
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static CategoryDto toDto(Category category) {
    return new CategoryDto(
        category.id().value(),
        category.name(),
        category.slug().value(),
        category.description(),
        category.status(),
        category.imageId(),
        category.position(),
        category.createdAt(),
        category.updatedAt());
  }
}
