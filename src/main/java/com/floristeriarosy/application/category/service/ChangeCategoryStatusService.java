package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.port.in.ChangeCategoryStatusUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeCategoryStatusService implements ChangeCategoryStatusUseCase {

    @Override
    public void execute(ChangeCategoryStatusCommand command) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
