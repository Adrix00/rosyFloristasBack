package com.floristeriarosy.application.product.command;

import java.util.List;
import java.util.UUID;

/**
 * @param id the product whose categories are being set
 * @param categoryIds every category id the product should belong to; must not be empty
 */
public record UpdateProductCategoriesCommand(UUID id, List<UUID> categoryIds) {

  /** Defensively copies {@code categoryIds} (SpotBugs EI_EXPOSE_REP2). */
  public UpdateProductCategoriesCommand {
    categoryIds = List.copyOf(categoryIds);
  }
}
