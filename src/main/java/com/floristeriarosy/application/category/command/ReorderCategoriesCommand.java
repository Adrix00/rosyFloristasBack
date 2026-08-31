package com.floristeriarosy.application.category.command;

import java.util.List;
import java.util.UUID;

public record ReorderCategoriesCommand(List<UUID> categoryIds) {

  public ReorderCategoriesCommand {
    categoryIds = List.copyOf(categoryIds);
  }
}
