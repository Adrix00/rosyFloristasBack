package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;

public interface UpdateCategoryUseCase {

  void execute(UpdateCategoryCommand command);
}
