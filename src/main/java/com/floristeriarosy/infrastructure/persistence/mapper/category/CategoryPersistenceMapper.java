package com.floristeriarosy.infrastructure.persistence.mapper.category;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.infrastructure.persistence.entity.category.CategoryEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class CategoryPersistenceMapper {

  /**
   * @param category the domain category to persist
   * @return its JPA entity shape
   */
  public CategoryEntity toEntity(Category category) {
    return new CategoryEntity(
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

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain category ({@link Category#reconstitute})
   */
  public Category toDomain(CategoryEntity entity) {
    return Category.reconstitute(
        CategoryId.of(entity.getId()),
        entity.getName(),
        CategorySlug.of(entity.getSlug()),
        entity.getDescription(),
        entity.getStatus(),
        entity.getImageId(),
        entity.getPosition(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
