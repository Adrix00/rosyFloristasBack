package com.floristeriarosy.application.product.command;

import java.util.List;
import java.util.UUID;

/**
 * @param id the product the suggestions are attached to
 * @param extraProductIds every {@code is_extra = true} product to suggest, in display order; may
 *     be empty
 */
public record UpdateProductExtrasCommand(UUID id, List<UUID> extraProductIds) {

  /** Defensively copies {@code extraProductIds} (SpotBugs EI_EXPOSE_REP2). */
  public UpdateProductExtrasCommand {
    extraProductIds = List.copyOf(extraProductIds);
  }
}
