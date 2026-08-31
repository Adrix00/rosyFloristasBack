package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;

/** Full-catalog reorder in one transaction (category.md, section 7). */
public interface ReorderCategoriesUseCase {

  /**
   * @param command every category id, in its new order
   */
  void execute(ReorderCategoriesCommand command);
}
