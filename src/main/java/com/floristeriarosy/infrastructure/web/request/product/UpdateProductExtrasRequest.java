package com.floristeriarosy.infrastructure.web.request.product;

import com.floristeriarosy.shared.validation.NoDuplicates;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Sent complete, in display order (product.md, section 5).
 *
 * @param extraProductIds every {@code is_extra = true} product to suggest; may be empty
 */
public record UpdateProductExtrasRequest(@NotNull @Size(max = 20) @NoDuplicates List<UUID> extraProductIds) {

  /**
   * Defensively copies {@code extraProductIds} when present (SpotBugs EI_EXPOSE_REP2); left
   * {@code null} through so {@code @NotNull} produces a clean 422 instead of an NPE.
   */
  public UpdateProductExtrasRequest {
    extraProductIds = extraProductIds == null ? null : List.copyOf(extraProductIds);
  }
}
