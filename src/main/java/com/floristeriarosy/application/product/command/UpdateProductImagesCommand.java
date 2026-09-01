package com.floristeriarosy.application.product.command;

import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import java.util.List;
import java.util.UUID;

/**
 * @param id the product whose gallery is being set
 * @param images every image the gallery should contain, in display order; may be empty
 */
public record UpdateProductImagesCommand(UUID id, List<ProductImageAssignment> images) {

  /** Defensively copies {@code images} (SpotBugs EI_EXPOSE_REP2). */
  public UpdateProductImagesCommand {
    images = List.copyOf(images);
  }
}
