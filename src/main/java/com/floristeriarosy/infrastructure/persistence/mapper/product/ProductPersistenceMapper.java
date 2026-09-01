package com.floristeriarosy.infrastructure.persistence.mapper.product;

import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.entity.product.ProductEntity;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). {@code search_text} is set
 * separately by the adapter (ADR-006), not derived here.
 */
@Component
public class ProductPersistenceMapper {

  /**
   * @param product the domain product to persist
   * @param searchText the normalized text to index for search (ADR-006)
   * @return its JPA entity shape
   */
  public ProductEntity toEntity(Product product, String searchText) {
    return new ProductEntity(
        product.id().value(),
        product.name(),
        product.slug().value(),
        product.description(),
        product.price(),
        product.stock(),
        product.lowStockThreshold(),
        product.status(),
        product.isExtra(),
        product.attributes(),
        searchText,
        product.createdAt(),
        product.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain product ({@link Product#reconstitute})
   */
  public Product toDomain(ProductEntity entity) {
    return Product.reconstitute(
        ProductId.of(entity.getId()),
        entity.getName(),
        ProductSlug.of(entity.getSlug()),
        entity.getDescription(),
        entity.getPrice(),
        entity.getStock(),
        entity.getLowStockThreshold(),
        entity.getStatus(),
        entity.isExtra(),
        entity.getAttributes(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
