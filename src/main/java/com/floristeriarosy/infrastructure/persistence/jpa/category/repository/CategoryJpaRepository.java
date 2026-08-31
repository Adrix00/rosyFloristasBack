package com.floristeriarosy.infrastructure.persistence.jpa.category.repository;

import com.floristeriarosy.infrastructure.persistence.entity.category.CategoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

  Optional<CategoryEntity> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
