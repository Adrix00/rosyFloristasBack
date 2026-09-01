package com.floristeriarosy.infrastructure.web.request.product;

import com.floristeriarosy.shared.validation.NoDuplicates;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param name required; the slug is generated from it, never supplied
 * @param description optional
 * @param price required
 * @param categoryIds required, non-empty; every category the product should belong to
 * @param isExtra optional, defaults to {@code false}
 * @param attributes optional; validated against the declared definitions
 * @param imageIds optional; the product's gallery, in display order
 * @param initialStock optional; stock to activate managed inventory with
 */
public record CreateProductRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 5000) String description,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
    @NotEmpty @Size(max = 20) @NoDuplicates List<UUID> categoryIds,
    Boolean isExtra,
    Map<String, Object> attributes,
    @Size(max = 10) @NoDuplicates List<UUID> imageIds,
    @PositiveOrZero Integer initialStock) {

  /**
   * Defensively copies {@code categoryIds}, {@code attributes} and {@code imageIds} when present
   * (SpotBugs EI_EXPOSE_REP2); left {@code null} through so the field's own validation (or the
   * mapper's null-safe default) applies instead of an NPE here.
   */
  public CreateProductRequest {
    categoryIds = categoryIds == null ? null : List.copyOf(categoryIds);
    attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    imageIds = imageIds == null ? null : List.copyOf(imageIds);
  }
}
