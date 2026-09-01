package com.floristeriarosy.infrastructure.persistence.jpa.product.repository;

import com.floristeriarosy.infrastructure.persistence.entity.product.ProductEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link ProductEntity}: writes and simple lookups (ADR-002). */
public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

  /**
   * @param slug the product to load
   * @return the entity, if it exists
   */
  Optional<ProductEntity> findBySlug(String slug);

  /**
   * @param slug the slug to check
   * @return whether a product already uses it
   */
  boolean existsBySlug(String slug);
}
