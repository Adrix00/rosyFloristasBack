package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.port.in.DeleteCategoryUseCase;
import com.floristeriarosy.application.category.port.out.CategoryExistencePort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteCategoryService implements DeleteCategoryUseCase {

  private final CategoryExistencePort existencePort;
  private final CategoryWritePort writePort;

  public DeleteCategoryService(CategoryExistencePort existencePort, CategoryWritePort writePort) {
    this.existencePort = existencePort;
    this.writePort = writePort;
  }

  @Override
  public void execute(DeleteCategoryCommand command) {
    CategoryId id = CategoryId.of(command.id());
    if (!existencePort.existsById(id)) {
      throw new CategoryNotFoundException("Category " + id + " not found");
    }
    // CASCADE en product_categories; los productos sobreviven (category.md, sección 3.3).
    writePort.delete(id);
  }
}
