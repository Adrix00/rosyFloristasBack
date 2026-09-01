package com.floristeriarosy.infrastructure.web.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sent complete, in display order (product.md, section 5).
 *
 * @param images the product's full gallery; may be empty
 */
public record UpdateProductImagesRequest(@NotNull @Valid @Size(max = 10) List<ProductImageItemRequest> images) {

  /**
   * Defensively copies {@code images} when present (SpotBugs EI_EXPOSE_REP2); left {@code null}
   * through so {@code @NotNull} produces a clean 422 instead of an NPE.
   */
  public UpdateProductImagesRequest {
    images = images == null ? null : List.copyOf(images);
  }
}
