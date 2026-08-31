package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;

public interface ReorderCategoriesUseCase {

  void execute(ReorderCategoriesCommand command);
}
