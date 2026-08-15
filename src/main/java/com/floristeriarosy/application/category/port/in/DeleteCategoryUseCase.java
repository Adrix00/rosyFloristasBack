package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;

public interface DeleteCategoryUseCase {

    void execute(DeleteCategoryCommand command);

}
