package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.port.in.DeleteCategoryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteCategoryService implements DeleteCategoryUseCase {

  @Override
  public void execute(DeleteCategoryCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
