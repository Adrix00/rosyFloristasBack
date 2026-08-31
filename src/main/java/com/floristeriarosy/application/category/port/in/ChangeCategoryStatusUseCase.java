package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;

public interface ChangeCategoryStatusUseCase {

  CategoryDto execute(ChangeCategoryStatusCommand command);
}
