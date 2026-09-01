package com.floristeriarosy.infrastructure.web.request.product;

import com.floristeriarosy.shared.validation.NoDuplicates;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Sent complete — this endpoint cannot empty the list (product.md, section 5).
 *
 * @param categoryIds every category id the product should belong to; must not be empty
 */
public record UpdateProductCategoriesRequest(@NotEmpty @Size(max = 20) @NoDuplicates List<UUID> categoryIds) {

  /**
   * Defensively copies {@code categoryIds} when present (SpotBugs EI_EXPOSE_REP2); left {@code
   * null} through so {@code @NotEmpty} produces a clean 422 instead of an NPE.
   */
  public UpdateProductCategoriesRequest {
    categoryIds = categoryIds == null ? null : List.copyOf(categoryIds);
  }
}
