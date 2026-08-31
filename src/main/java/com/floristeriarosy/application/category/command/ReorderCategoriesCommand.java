package com.floristeriarosy.application.category.command;

import java.util.List;
import java.util.UUID;

/**
 * @param categoryIds every category id, in its new order; defensively copied
 */
public record ReorderCategoriesCommand(List<UUID> categoryIds) {

  /** Defensively copies {@code categoryIds} (SpotBugs EI_EXPOSE_REP2). */
  public ReorderCategoriesCommand {
    categoryIds = List.copyOf(categoryIds);
  }
}
