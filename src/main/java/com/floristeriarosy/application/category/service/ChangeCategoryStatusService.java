package com.floristeriarosy.application.category.service;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.mapper.CategoryDtoMapper;
import com.floristeriarosy.application.category.port.in.ChangeCategoryStatusUseCase;
import com.floristeriarosy.application.category.port.out.CategoryReadPort;
import com.floristeriarosy.application.category.port.out.CategoryWritePort;
import com.floristeriarosy.domain.exception.category.CategoryNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeCategoryStatusService implements ChangeCategoryStatusUseCase {

  private final CategoryReadPort readPort;
  private final CategoryWritePort writePort;

  public ChangeCategoryStatusService(CategoryReadPort readPort, CategoryWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  @Override
  public CategoryDto execute(ChangeCategoryStatusCommand command) {
    CategoryId id = CategoryId.of(command.id());
    Category category =
        readPort
            .findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Category " + id + " not found"));

    // Idempotente: fijar el mismo estado dos veces es un 200 sin efecto (category.md, sección 10).
    category.changeStatus(command.status());
    return CategoryDtoMapper.toDto(writePort.save(category));
  }
}
