package com.floristeriarosy.infrastructure.web.request.category;

import com.floristeriarosy.shared.validation.NoDuplicates;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The position of each category is its index in the list (category.md §5). */
public record ReorderCategoriesRequest(
    @NotEmpty @Size(max = 200) @NoDuplicates List<UUID> categoryIds) {

  public ReorderCategoriesRequest {
    categoryIds = categoryIds == null ? null : List.copyOf(categoryIds);
  }
}
