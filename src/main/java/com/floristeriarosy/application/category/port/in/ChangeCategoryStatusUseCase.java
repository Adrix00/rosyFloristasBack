package com.floristeriarosy.application.category.port.in;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;

public interface ChangeCategoryStatusUseCase {

    void execute(ChangeCategoryStatusCommand command);

}
