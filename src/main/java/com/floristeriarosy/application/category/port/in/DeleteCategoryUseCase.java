package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;

/** Permanently deletes a category (category.md, section 7). */
public interface DeleteCategoryUseCase {

  /**
   * @param command id of the category to delete
   */
  void execute(DeleteCategoryCommand command);
}
