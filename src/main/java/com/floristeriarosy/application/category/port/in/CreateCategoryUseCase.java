package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

public interface CreateCategoryUseCase {

  CategoryDto execute(CreateCategoryCommand command);
}
