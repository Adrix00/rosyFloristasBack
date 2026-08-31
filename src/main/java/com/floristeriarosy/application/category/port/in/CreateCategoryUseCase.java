package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

/** Creates a category (category.md, section 7). */
public interface CreateCategoryUseCase {

  /**
   * @param command name, description, imageId and position of the category to create
   * @return the created category
   */
  CategoryDto execute(CreateCategoryCommand command);
}
