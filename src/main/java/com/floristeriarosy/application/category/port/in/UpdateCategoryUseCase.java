package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

public interface UpdateCategoryUseCase {

  CategoryDto execute(UpdateCategoryCommand command);
}
