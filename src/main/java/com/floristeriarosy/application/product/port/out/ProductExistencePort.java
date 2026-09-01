package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;

/** Checks product existence and commercial-history rules (ADR-003; product.md, section 8). */
public interface ProductExistencePort {

  /**
   * @param id the product to check
   * @return whether it exists
   */
  boolean existsById(ProductId id);

  /**
   * @param slug the slug to check
   * @return whether a product already uses it
   */
  boolean existsBySlug(String slug);

  /**
   * Counts, per source, the commercial history that would block a physical delete (product.md,
   * section 3.10, section 6: {@code ProductDeletionImpactResponse}).
   *
   * @param id the product being previewed for deletion
   * @return the impact preview
   */
  ProductDeletionImpact deletionImpact(ProductId id);
}
