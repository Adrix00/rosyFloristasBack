package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.port.in.CreateCategoryUseCase;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateCategoryService implements CreateCategoryUseCase {

  @Override
  public CategoryId execute(CreateCategoryCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
