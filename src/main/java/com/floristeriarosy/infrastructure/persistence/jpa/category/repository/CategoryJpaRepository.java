package com.floristeriarosy.infrastructure.persistence.jpa.category.repository;

import com.floristeriarosy.infrastructure.persistence.entity.category.CategoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link CategoryEntity}: writes and simple lookups (ADR-002). */
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

  /**
   * @param slug the category to load
   * @return the entity, if it exists
   */
  Optional<CategoryEntity> findBySlug(String slug);

  /**
   * @param slug the slug to check
   * @return whether a category already uses it
   */
  boolean existsBySlug(String slug);
}
