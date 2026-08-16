package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.port.in.UpdateCategoryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateCategoryService implements UpdateCategoryUseCase {

  @Override
  public void execute(UpdateCategoryCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
