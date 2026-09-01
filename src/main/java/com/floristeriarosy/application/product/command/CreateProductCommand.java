package com.floristeriarosy.application.product.command;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param name the product name; the slug is generated from it, never supplied
 * @param description optional description
 * @param price the base price
 * @param categoryIds every category the product should belong to; must not be empty (product.md,
 *     section 3.4)
 * @param isExtra whether this product may be offered as a suggested extra
 * @param attributes attribute values to validate against the declared definitions
 * @param imageIds the product's gallery, in display order; may be empty
 * @param initialStock stock to activate inventory management with, or {@code null} for unmanaged
 *     inventory
 */
public record CreateProductCommand(
    String name,
    String description,
    BigDecimal price,
    List<UUID> categoryIds,
    boolean isExtra,
    Map<String, Object> attributes,
    List<UUID> imageIds,
    Integer initialStock) {

  /**
   * Defensively copies {@code categoryIds}, {@code attributes} and {@code imageIds} (SpotBugs
   * EI_EXPOSE_REP2). {@code attributes} uses a plain {@link LinkedHashMap}, not {@link
   * Map#copyOf}, which rejects a {@code null} value.
   */
  public CreateProductCommand {
    categoryIds = List.copyOf(categoryIds);
    attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    imageIds = List.copyOf(imageIds);
  }
}
