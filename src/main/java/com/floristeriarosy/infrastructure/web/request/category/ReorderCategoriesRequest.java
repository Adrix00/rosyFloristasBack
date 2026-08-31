package com.floristeriarosy.infrastructure.web.request.category;

import com.floristeriarosy.shared.validation.NoDuplicates;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The position of each category is its index in the list (category.md §5).
 *
 * @param categoryIds every existing category id, in its new order; must contain no duplicates
 */
public record ReorderCategoriesRequest(
    @NotEmpty @Size(max = 200) @NoDuplicates List<UUID> categoryIds) {

  /**
   * Defensively copies {@code categoryIds} when present (SpotBugs EI_EXPOSE_REP2); left {@code
   * null} through so {@code @NotEmpty} produces a clean 422 instead of an NPE.
   */
  public ReorderCategoriesRequest {
    categoryIds = categoryIds == null ? null : List.copyOf(categoryIds);
  }
}
