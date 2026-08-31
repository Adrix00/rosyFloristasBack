package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

/** Full replace ({@code PUT}) of an existing category (category.md, section 7). */
public interface UpdateCategoryUseCase {

  /**
   * @param command id of the category to update, plus its new field values
   * @return the updated category
   */
  CategoryDto execute(UpdateCategoryCommand command);
}
