package com.floristeriarosy.infrastructure.web.request.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full replace of the product's own fields. Stock, categories, images and suggested extras each
 * have their own endpoint (product.md, section 5).
 *
 * @param name required; the slug is regenerated from it
 * @param description optional
 * @param price required
 * @param isExtra optional, defaults to {@code false}
 * @param attributes optional; validated against the declared definitions
 */
public record UpdateProductRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 5000) String description,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
    Boolean isExtra,
    Map<String, Object> attributes) {

  /**
   * Defensively copies {@code attributes} when present (SpotBugs EI_EXPOSE_REP2); left {@code
   * null} through so the mapper's null-safe default applies instead of an NPE here.
   */
  public UpdateProductRequest {
    attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
  }
}
