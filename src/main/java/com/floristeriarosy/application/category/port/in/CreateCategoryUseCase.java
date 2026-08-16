package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;

public interface CreateCategoryUseCase {

  CategoryId execute(CreateCategoryCommand command);
}
