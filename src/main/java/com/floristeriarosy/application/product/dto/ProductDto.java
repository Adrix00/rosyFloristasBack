package com.floristeriarosy.application.product.dto;

import com.floristeriarosy.domain.model.product.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read shape of a product returned by write and read use cases. Kept outside {@code domain} so
 * Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param name the product name
 * @param slug the generated slug
 * @param description the description, or {@code null}
 * @param price the base price
 * @param effectivePrice the price with an active discount applied, if any; otherwise equal to
 *     {@code price}
 * @param onSale whether {@code effectivePrice} differs from {@code price}
 * @param status {@code ACTIVE}, {@code INACTIVE} or {@code DISCONTINUED}
 * @param isExtra whether this product may be offered as a suggested extra
 * @param attributes the attribute values, keyed by declared attribute key
 * @param categories the categories this product belongs to
 * @param images the product's image gallery, ordered by position
 * @param stock the current stock, or {@code null} if inventory is unmanaged; admin-only
 *     (product.md, section 6)
 * @param inventoryManaged whether {@code stock} is non-{@code null}
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record ProductDto(
    UUID id,
    String name,
    String slug,
    String description,
    BigDecimal price,
    BigDecimal effectivePrice,
    boolean onSale,
    ProductStatus status,
    boolean isExtra,
    Map<String, Object> attributes,
    List<ProductCategoryRef> categories,
    List<ProductImageRef> images,
    Integer stock,
    boolean inventoryManaged,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Defensively copies {@code attributes}, {@code categories} and {@code images} (SpotBugs
   * EI_EXPOSE_REP2). {@code attributes} uses a plain {@link LinkedHashMap}, not {@link
   * Map#copyOf}, which rejects a {@code null} value.
   */
  public ProductDto {
    attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    categories = List.copyOf(categories);
    images = List.copyOf(images);
  }
}
