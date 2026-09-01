package com.floristeriarosy.application.product.command;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @param id the product to update
 * @param name the new name; the slug is regenerated from it, breaking the previous link
 *     (product.md, section 3.1)
 * @param description the new description, or {@code null} to clear it
 * @param price the new base price
 * @param isExtra the new extra flag
 * @param attributes the new attribute values to validate against the declared definitions
 */
public record UpdateProductCommand(
    UUID id, String name, String description, BigDecimal price, boolean isExtra, Map<String, Object> attributes) {

  /**
   * Defensively copies {@code attributes} (SpotBugs EI_EXPOSE_REP2), using a plain {@link
   * LinkedHashMap} rather than {@link Map#copyOf}, which rejects a {@code null} value.
   */
  public UpdateProductCommand {
    attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
  }
}
